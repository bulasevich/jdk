/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "memory/allocation.inline.hpp"
#include "opto/addnode.hpp"
#include "opto/callnode.hpp"
#include "opto/loopnode.hpp"
#include "opto/movenode.hpp"
#include "opto/node.hpp"
#include "opto/opaquenode.hpp"
#include "opto/predicates.hpp"

//------------------------------split_thru_region------------------------------
// Split Node 'n' through merge point.
RegionNode* PhaseIdealLoop::split_thru_region(Node* n, RegionNode* region) {
  assert(n->is_CFG(), "");
  RegionNode* r = new RegionNode(region->req());
  IdealLoopTree* loop = get_loop(n);
  for (uint i = 1; i < region->req(); i++) {
    Node* x = n->clone();
    Node* in0 = n->in(0);
    if (in0->in(0) == region) x->set_req(0, in0->in(i));
    for (uint j = 1; j < n->req(); j++) {
      Node* in = n->in(j);
      if (get_ctrl(in) == region) {
        x->set_req(j, in->in(i));
      }
    }
    _igvn.register_new_node_with_optimizer(x);
    set_loop(x, loop);
    set_idom(x, x->in(0), dom_depth(x->in(0))+1);
    r->init_req(i, x);
  }

  // Record region
  r->set_req(0,region);         // Not a TRUE RegionNode
  _igvn.register_new_node_with_optimizer(r);
  set_loop(r, loop);
  if (!loop->_child) {
    loop->_body.push(r);
  }
  return r;
}

//------------------------------split_up---------------------------------------
// Split block-local op up through the phis to empty the current block
bool PhaseIdealLoop::split_up( Node *n, Node *blk1, Node *blk2 ) {
  if( n->is_CFG() ) {
    assert( n->in(0) != blk1, "Lousy candidate for split-if" );
    return false;
  }
  if (!at_relevant_ctrl(n, blk1, blk2))
    return false;               // Not block local
  if( n->is_Phi() ) return false; // Local PHIs are expected

  // Recursively split-up inputs
  for (uint i = 1; i < n->req(); i++) {
    if( split_up( n->in(i), blk1, blk2 ) ) {
      // Got split recursively and self went dead?
      if (n->outcnt() == 0)
        _igvn.remove_dead_node(n);
      return true;
    }
  }

  if (clone_cmp_loadklass_down(n, blk1, blk2)) {
    return true;
  }

  // Check for needing to clone-up a compare.  Can't do that, it forces
  // another (nested) split-if transform.  Instead, clone it "down".
  if (clone_cmp_down(n, blk1, blk2)) {
    return true;
  }

  clone_template_assertion_expression_down(n);

  if (n->Opcode() == Op_OpaqueZeroTripGuard) {
    // If this Opaque1 is part of the zero trip guard for a loop:
    // 1- it can't be shared
    // 2- the zero trip guard can't be the if that's being split
    // As a consequence, this node could be assigned control anywhere between its current control and the zero trip guard.
    // Move it down to get it out of the way of split if and avoid breaking the zero trip guard shape.
    Node* cmp = n->unique_out();
    assert(cmp->Opcode() == Op_CmpI, "bad zero trip guard shape");
    Node* bol = cmp->unique_out();
    assert(bol->Opcode() == Op_Bool, "bad zero trip guard shape");
    Node* iff = bol->unique_out();
    assert(iff->Opcode() == Op_If, "bad zero trip guard shape");
    set_ctrl(n, iff->in(0));
    set_ctrl(cmp, iff->in(0));
    set_ctrl(bol, iff->in(0));
    return true;
  }

  // See if splitting-up a Store.  Any anti-dep loads must go up as
  // well.  An anti-dep load might be in the wrong block, because in
  // this particular layout/schedule we ignored anti-deps and allow
  // memory to be alive twice.  This only works if we do the same
  // operations on anti-dep loads as we do their killing stores.
  if( n->is_Store() && n->in(MemNode::Memory)->in(0) == n->in(0) ) {
    // Get store's memory slice
    int alias_idx = C->get_alias_index(_igvn.type(n->in(MemNode::Address))->is_ptr());

    // Get memory-phi anti-dep loads will be using
    Node *memphi = n->in(MemNode::Memory);
    assert( memphi->is_Phi(), "" );
    // Hoist any anti-dep load to the splitting block;
    // it will then "split-up".
    for (DUIterator_Fast imax,i = memphi->fast_outs(imax); i < imax; i++) {
      Node *load = memphi->fast_out(i);
      if( load->is_Load() && alias_idx == C->get_alias_index(_igvn.type(load->in(MemNode::Address))->is_ptr()) )
        set_ctrl(load,blk1);
    }
  }

  // ConvI2L may have type information on it which becomes invalid if
  // it moves up in the graph so change any clones so widen the type
  // to TypeLong::INT when pushing it up.
  const Type* rtype = nullptr;
  if (n->Opcode() == Op_ConvI2L && n->bottom_type() != TypeLong::INT) {
    rtype = TypeLong::INT;
  }

  // Now actually split-up this guy.  One copy per control path merging.
  Node *phi = PhiNode::make_blank(blk1, n);
  for( uint j = 1; j < blk1->req(); j++ ) {
    Node *x = n->clone();
    // Widen the type of the ConvI2L when pushing up.
    if (rtype != nullptr) x->as_Type()->set_type(rtype);
    if( n->in(0) && n->in(0) == blk1 )
      x->set_req( 0, blk1->in(j) );
    for( uint i = 1; i < n->req(); i++ ) {
      Node *m = n->in(i);
      if( get_ctrl(m) == blk1 ) {
        assert( m->in(0) == blk1, "" );
        x->set_req( i, m->in(j) );
      }
    }
    register_new_node( x, blk1->in(j) );
    phi->init_req( j, x );
  }
  // Announce phi to optimizer
  register_new_node(phi, blk1);

  // Remove cloned-up value from optimizer; use phi instead
  _igvn.replace_node( n, phi );

  // (There used to be a self-recursive call to split_up() here,
  // but it is not needed.  All necessary forward walking is done
  // by do_split_if() below.)

  return true;
}

// Look for a (If .. (Bool(CmpP (LoadKlass .. (AddP obj ..)) ..))) and clone all of it down.
// There's likely a CheckCastPP on one of the branches of the If, with obj as input.
// If the (LoadKlass .. (AddP obj ..)) is not cloned down, then split if transforms this to: (If .. (Bool(CmpP phi1 ..)))
// and the CheckCastPP to (CheckCastPP phi2). It's possible then that phi2 is transformed to a CheckCastPP
// (through PhiNode::Ideal) and that that CheckCastPP is replaced by another narrower CheckCastPP at the same control
// (through ConstraintCastNode::Identity). That could cause the CheckCastPP at the If to become top while (CmpP phi1)
// wouldn't constant fold because it's using a different data path. Cloning the whole subgraph down guarantees both the
// AddP and CheckCastPP have the same obj input after split if.
bool PhaseIdealLoop::clone_cmp_loadklass_down(Node* n, const Node* blk1, const Node* blk2) {
  if (n->Opcode() == Op_AddP && at_relevant_ctrl(n, blk1, blk2)) {
    Node_List cmp_nodes;
    uint old = C->unique();
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* u1 = n->fast_out(i);
      if (u1->Opcode() == Op_LoadNKlass && at_relevant_ctrl(u1, blk1, blk2)) {
        for (DUIterator_Fast jmax, j = u1->fast_outs(jmax); j < jmax; j++) {
          Node* u2 = u1->fast_out(j);
          if (u2->Opcode() == Op_DecodeNKlass && at_relevant_ctrl(u2, blk1, blk2)) {
            for (DUIterator k = u2->outs(); u2->has_out(k); k++) {
              Node* u3 = u2->out(k);
              if (at_relevant_ctrl(u3, blk1, blk2) && clone_cmp_down(u3, blk1, blk2)) {
                --k;
              }
            }
            for (DUIterator_Fast kmax, k = u2->fast_outs(kmax); k < kmax; k++) {
              Node* u3 = u2->fast_out(k);
              if (u3->_idx >= old) {
                cmp_nodes.push(u3);
              }
            }
          }
        }
      } else if (u1->Opcode() == Op_LoadKlass && at_relevant_ctrl(u1, blk1, blk2)) {
        for (DUIterator j = u1->outs(); u1->has_out(j); j++) {
          Node* u2 = u1->out(j);
          if (at_relevant_ctrl(u2, blk1, blk2) && clone_cmp_down(u2, blk1, blk2)) {
            --j;
          }
        }
        for (DUIterator_Fast kmax, k = u1->fast_outs(kmax); k < kmax; k++) {
          Node* u2 = u1->fast_out(k);
          if (u2->_idx >= old) {
            cmp_nodes.push(u2);
          }
        }
      }
    }

    for (uint i = 0; i < cmp_nodes.size(); ++i) {
      Node* cmp = cmp_nodes.at(i);
      clone_loadklass_nodes_at_cmp_index(n, cmp, 1);
      clone_loadklass_nodes_at_cmp_index(n, cmp, 2);
    }
    if (n->outcnt() == 0) {
      assert(n->is_dead(), "");
      return true;
    }
  }
  return false;
}

bool PhaseIdealLoop::at_relevant_ctrl(Node* n, const Node* blk1, const Node* blk2) {
  return ctrl_or_self(n) == blk1 || ctrl_or_self(n) == blk2;
}

void PhaseIdealLoop::clone_loadklass_nodes_at_cmp_index(const Node* n, Node* cmp, int i) {
  Node* decode = cmp->in(i);
  if (decode->Opcode() == Op_DecodeNKlass) {
    Node* loadklass = decode->in(1);
    if (loadklass->Opcode() == Op_LoadNKlass) {
      Node* addp = loadklass->in(MemNode::Address);
      if (addp == n) {
        Node* ctrl = get_ctrl(cmp);
        Node* decode_clone = decode->clone();
        Node* loadklass_clone = loadklass->clone();
        Node* addp_clone = addp->clone();
        register_new_node(decode_clone, ctrl);
        register_new_node(loadklass_clone, ctrl);
        register_new_node(addp_clone, ctrl);
        _igvn.replace_input_of(cmp, i, decode_clone);
        _igvn.replace_input_of(decode_clone, 1, loadklass_clone);
        _igvn.replace_input_of(loadklass_clone, MemNode::Address, addp_clone);
        if (decode->outcnt() == 0) {
          _igvn.remove_dead_node(decode);
        }
      }
    }
  } else {
    Node* loadklass = cmp->in(i);
    if (loadklass->Opcode() == Op_LoadKlass) {
      Node* addp = loadklass->in(MemNode::Address);
      if (addp == n) {
        Node* ctrl = get_ctrl(cmp);
        Node* loadklass_clone = loadklass->clone();
        Node* addp_clone = addp->clone();
        register_new_node(loadklass_clone, ctrl);
        register_new_node(addp_clone, ctrl);
        _igvn.replace_input_of(cmp, i, loadklass_clone);
        _igvn.replace_input_of(loadklass_clone, MemNode::Address, addp_clone);
        if (loadklass->outcnt() == 0) {
          _igvn.remove_dead_node(loadklass);
        }
      }
    }
  }
}

bool PhaseIdealLoop::clone_cmp_down(Node* n, const Node* blk1, const Node* blk2) {
  if( n->is_Cmp() ) {
    assert(get_ctrl(n) == blk2 || get_ctrl(n) == blk1, "must be in block with IF");
    // Check for simple Cmp/Bool/CMove which we can clone-up.  Cmp/Bool/CMove
    // sequence can have no other users and it must all reside in the split-if
    // block.  Non-simple Cmp/Bool/CMove sequences are 'cloned-down' below -
    // private, per-use versions of the Cmp and Bool are made.  These sink to
    // the CMove block.  If the CMove is in the split-if block, then in the
    // next iteration this will become a simple Cmp/Bool/CMove set to clone-up.
    Node *bol, *cmov;
    if (!(n->outcnt() == 1 && n->unique_out()->is_Bool() &&
          (bol = n->unique_out()->as_Bool()) &&
          (at_relevant_ctrl(bol, blk1, blk2) &&
           bol->outcnt() == 1 &&
           bol->unique_out()->is_CMove() &&
           (cmov = bol->unique_out()->as_CMove()) &&
           at_relevant_ctrl(cmov, blk1, blk2)))) {

      // Must clone down
      if (!n->is_FastLock()) {
        // Clone down any block-local BoolNode uses of this CmpNode
        for (DUIterator i = n->outs(); n->has_out(i); i++) {
          Node* bol = n->out(i);
          assert( bol->is_Bool(), "" );
          if (bol->outcnt() == 1) {
            Node* use = bol->unique_out();
            if (use->is_OpaqueNotNull() || use->is_OpaqueTemplateAssertionPredicate() ||
                use->is_OpaqueInitializedAssertionPredicate()) {
              if (use->outcnt() == 1) {
                Node* iff = use->unique_out();
                assert(iff->is_If(), "unexpected node type");
                Node *use_c = iff->in(0);
                if (use_c == blk1 || use_c == blk2) {
                  continue;
                }
              }
            } else {
              // We might see an Opaque1 from a loop limit check here
              assert(use->is_If() || use->is_CMove() || use->Opcode() == Op_Opaque1 || use->is_AllocateArray(), "unexpected node type");
              Node *use_c = (use->is_If() || use->is_AllocateArray()) ? use->in(0) : get_ctrl(use);
              if (use_c == blk1 || use_c == blk2) {
                assert(use->is_CMove(), "unexpected node type");
                continue;
              }
            }
          }
          if (at_relevant_ctrl(bol, blk1, blk2)) {
            // Recursively sink any BoolNode
            for (DUIterator j = bol->outs(); bol->has_out(j); j++) {
              Node* u = bol->out(j);
              // Uses are either IfNodes, CMoves, OpaqueNotNull, or Opaque*AssertionPredicate
              if (u->is_OpaqueNotNull() || u->is_OpaqueTemplateAssertionPredicate() ||
                  u->is_OpaqueInitializedAssertionPredicate()) {
                assert(u->in(1) == bol, "bad input");
                for (DUIterator_Last kmin, k = u->last_outs(kmin); k >= kmin; --k) {
                  Node* iff = u->last_out(k);
                  assert(iff->is_If() || iff->is_CMove(), "unexpected node type");
                  assert( iff->in(1) == u, "" );
                  // Get control block of either the CMove or the If input
                  Node *iff_ctrl = iff->is_If() ? iff->in(0) : get_ctrl(iff);
                  Node *x1 = bol->clone();
                  Node *x2 = u->clone();
                  register_new_node(x1, iff_ctrl);
                  register_new_node(x2, iff_ctrl);
                  _igvn.replace_input_of(x2, 1, x1);
                  _igvn.replace_input_of(iff, 1, x2);
                }
                _igvn.remove_dead_node(u);
                --j;
              } else {
                // We might see an Opaque1 from a loop limit check here
                assert(u->is_If() || u->is_CMove() || u->Opcode() == Op_Opaque1 || u->is_AllocateArray(), "unexpected node type");
                assert(u->is_AllocateArray() || u->in(1) == bol, "");
                assert(!u->is_AllocateArray() || u->in(AllocateNode::ValidLengthTest) == bol, "wrong input to AllocateArray");
                // Get control block of either the CMove or the If input
                Node *u_ctrl = (u->is_If() || u->is_AllocateArray()) ? u->in(0) : get_ctrl(u);
                assert((u_ctrl != blk1 && u_ctrl != blk2) || u->is_CMove(), "won't converge");
                Node *x = bol->clone();
                register_new_node(x, u_ctrl);
                _igvn.replace_input_of(u, u->is_AllocateArray() ? AllocateNode::ValidLengthTest : 1, x);
                --j;
              }
            }
            _igvn.remove_dead_node(bol);
            --i;
          }
        }
      }
      // Clone down this CmpNode
      for (DUIterator_Last jmin, j = n->last_outs(jmin); j >= jmin; --j) {
        Node* use = n->last_out(j);
        uint pos = 1;
        if (n->is_FastLock()) {
          pos = TypeFunc::Parms + 2;
          assert(use->is_Lock(), "FastLock only used by LockNode");
        }
        assert(use->in(pos) == n, "" );
        Node *x = n->clone();
        register_new_node(x, ctrl_or_self(use));
        _igvn.replace_input_of(use, pos, x);
      }
      _igvn.remove_dead_node(n);

      return true;
    }
  }
  return false;
}

// 'n' could be a node belonging to a Template Assertion Expression (i.e. any node between a Template Assertion Predicate
// and its OpaqueLoop* nodes (included)). We cannot simply split this node up since this would  create a phi node inside
// the Template Assertion Expression - making it unrecognizable as such. Therefore, we completely clone the entire
// Template Assertion Expression "down". This ensures that we have an untouched copy that is still recognized by the
// Template Assertion Predicate matching code.
void PhaseIdealLoop::clone_template_assertion_expression_down(Node* node) {
  if (!TemplateAssertionExpressionNode::is_in_expression(node)) {
    return;
  }

  TemplateAssertionExpressionNode template_assertion_expression_node(node);
  auto clone_expression = [&](IfNode* template_assertion_predicate) {
    OpaqueTemplateAssertionPredicateNode* opaque_node =
        template_assertion_predicate->in(1)->as_OpaqueTemplateAssertionPredicate();
    TemplateAssertionExpression template_assertion_expression(opaque_node, this);
    Node* new_control = template_assertion_predicate->in(0);
    OpaqueTemplateAssertionPredicateNode* cloned_opaque_node = template_assertion_expression.clone(new_control,
                                                                                                   opaque_node->loop_node());
    igvn().replace_input_of(template_assertion_predicate, 1, cloned_opaque_node);
  };
  template_assertion_expression_node.for_each_template_assertion_predicate(clone_expression);
}

//------------------------------register_new_node------------------------------
void PhaseIdealLoop::register_new_node( Node *n, Node *blk ) {
  assert(!n->is_CFG(), "must be data node");
  _igvn.register_new_node_with_optimizer(n);
  set_ctrl(n, blk);
  IdealLoopTree *loop = get_loop(blk);
  if( !loop->_child )
    loop->_body.push(n);
}

//------------------------------small_cache------------------------------------
struct small_cache : public Dict {

  small_cache() : Dict( cmpkey, hashptr ) {}
  Node *probe( Node *use_blk ) { return (Node*)((*this)[use_blk]); }
  void lru_insert( Node *use_blk, Node *new_def ) { Insert(use_blk,new_def); }
};

//------------------------------spinup-----------------------------------------
// "Spin up" the dominator tree, starting at the use site and stopping when we
// find the post-dominating point.

// We must be at the merge point which post-dominates 'new_false' and
// 'new_true'.  Figure out which edges into the RegionNode eventually lead up
// to false and which to true.  Put in a PhiNode to merge values; plug in
// the appropriate false-arm or true-arm values.  If some path leads to the
// original IF, then insert a Phi recursively.
Node *PhaseIdealLoop::spinup( Node *iff_dom, Node *new_false, Node *new_true, Node *use_blk, Node *def, small_cache *cache ) {
  if (use_blk->is_top())        // Handle dead uses
    return use_blk;
  Node *prior_n = (Node*)((intptr_t)0xdeadbeef);
  Node *n = use_blk;            // Get path input
  assert( use_blk != iff_dom, "" );
  // Here's the "spinup" the dominator tree loop.  Do a cache-check
  // along the way, in case we've come this way before.
  while( n != iff_dom ) {       // Found post-dominating point?
    prior_n = n;
    n = idom(n);                // Search higher
    Node *s = cache->probe( prior_n ); // Check cache
    if( s ) return s;           // Cache hit!
  }

  Node *phi_post;
  if( prior_n == new_false || prior_n == new_true ) {
    phi_post = def->clone();
    phi_post->set_req(0, prior_n );
    register_new_node(phi_post, prior_n);
  } else {
    // This method handles both control uses (looking for Regions) or data
    // uses (looking for Phis).  If looking for a control use, then we need
    // to insert a Region instead of a Phi; however Regions always exist
    // previously (the hash_find_insert below would always hit) so we can
    // return the existing Region.
    if( def->is_CFG() ) {
      phi_post = prior_n;       // If looking for CFG, return prior
    } else {
      assert( def->is_Phi(), "" );
      assert( prior_n->is_Region(), "must be a post-dominating merge point" );

      // Need a Phi here
      phi_post = PhiNode::make_blank(prior_n, def);
      // Search for both true and false on all paths till find one.
      for( uint i = 1; i < phi_post->req(); i++ ) // For all paths
        phi_post->init_req( i, spinup( iff_dom, new_false, new_true, prior_n->in(i), def, cache ) );
      Node *t = _igvn.hash_find_insert(phi_post);
      if( t ) {                 // See if we already have this one
        // phi_post will not be used, so kill it
        _igvn.remove_dead_node(phi_post);
        phi_post->destruct(&_igvn);
        phi_post = t;
      } else {
        register_new_node( phi_post, prior_n );
      }
    }
  }

  // Update cache everywhere
  prior_n = (Node*)((intptr_t)0xdeadbeef);  // Reset IDOM walk
  n = use_blk;                  // Get path input
  // Spin-up the idom tree again, basically doing path-compression.
  // Insert cache entries along the way, so that if we ever hit this
  // point in the IDOM tree again we'll stop immediately on a cache hit.
  while( n != iff_dom ) {       // Found post-dominating point?
    prior_n = n;
    n = idom(n);                // Search higher
    cache->lru_insert( prior_n, phi_post ); // Fill cache
  } // End of while not gone high enough

  return phi_post;
}

//------------------------------find_use_block---------------------------------
// Find the block a USE is in.  Normally USE's are in the same block as the
// using instruction.  For Phi-USE's, the USE is in the predecessor block
// along the corresponding path.
Node *PhaseIdealLoop::find_use_block( Node *use, Node *def, Node *old_false, Node *new_false, Node *old_true, Node *new_true ) {
  // CFG uses are their own block
  if( use->is_CFG() )
    return use;

  if( use->is_Phi() ) {         // Phi uses in prior block
    // Grab the first Phi use; there may be many.
    // Each will be handled as a separate iteration of
    // the "while( phi->outcnt() )" loop.
    uint j;
    for( j = 1; j < use->req(); j++ )
      if( use->in(j) == def )
        break;
    assert( j < use->req(), "def should be among use's inputs" );
    return use->in(0)->in(j);
  }
  // Normal (non-phi) use
  Node *use_blk = get_ctrl(use);
  // Some uses are directly attached to the old (and going away)
  // false and true branches.
  if( use_blk == old_false ) {
    use_blk = new_false;
    set_ctrl(use, new_false);
  }
  if( use_blk == old_true ) {
    use_blk = new_true;
    set_ctrl(use, new_true);
  }

  if (use_blk == nullptr) {        // He's dead, Jim
    _igvn.replace_node(use, C->top());
  }

  return use_blk;
}

//------------------------------handle_use-------------------------------------
// Handle uses of the merge point.  Basically, split-if makes the merge point
// go away so all uses of the merge point must go away as well.  Most block
// local uses have already been split-up, through the merge point.  Uses from
// far below the merge point can't always be split up (e.g., phi-uses are
// pinned) and it makes too much stuff live.  Instead we use a path-based
// solution to move uses down.
//
// If the use is along the pre-split-CFG true branch, then the new use will
// be from the post-split-CFG true merge point.  Vice-versa for the false
// path.  Some uses will be along both paths; then we sink the use to the
// post-dominating location; we may need to insert a Phi there.
void PhaseIdealLoop::handle_use( Node *use, Node *def, small_cache *cache, Node *region_dom, Node *new_false, Node *new_true, Node *old_false, Node *old_true ) {

  Node *use_blk = find_use_block(use,def,old_false,new_false,old_true,new_true);
  if( !use_blk ) return;        // He's dead, Jim

  // Walk up the dominator tree until I hit either the old IfFalse, the old
  // IfTrue or the old If.  Insert Phis where needed.
  Node *new_def = spinup( region_dom, new_false, new_true, use_blk, def, cache );

  // Found where this USE goes.  Re-point him.
  uint i;
  for( i = 0; i < use->req(); i++ )
    if( use->in(i) == def )
      break;
  assert( i < use->req(), "def should be among use's inputs" );
  _igvn.replace_input_of(use, i, new_def);
}

//------------------------------do_split_if------------------------------------
// Found an If getting its condition-code input from a Phi in the same block.
// Split thru the Region.
void PhaseIdealLoop::do_split_if(Node* iff, RegionNode** new_false_region, RegionNode** new_true_region) {

  C->set_major_progress();
  RegionNode *region = iff->in(0)->as_Region();
  Node *region_dom = idom(region);

  // We are going to clone this test (and the control flow with it) up through
  // the incoming merge point.  We need to empty the current basic block.
  // Clone any instructions which must be in this block up through the merge
  // point.
  DUIterator i, j;
  bool progress = true;
  while (progress) {
    progress = false;
    for (i = region->outs(); region->has_out(i); i++) {
      Node* n = region->out(i);
      if( n == region ) continue;
      // The IF to be split is OK.
      if( n == iff ) continue;
      if( !n->is_Phi() ) {      // Found pinned memory op or such
        if (split_up(n, region, iff)) {
          i = region->refresh_out_pos(i);
          progress = true;
        }
        continue;
      }
      assert( n->in(0) == region, "" );

      // Recursively split up all users of a Phi
      for (j = n->outs(); n->has_out(j); j++) {
        Node* m = n->out(j);
        // If m is dead, throw it away, and declare progress
        if (_loop_or_ctrl[m->_idx] == nullptr) {
          _igvn.remove_dead_node(m);
          // fall through
        }
        else if (m != iff && split_up(m, region, iff)) {
          // fall through
        } else {
          continue;
        }
        // Something unpredictable changed.
        // Tell the iterators to refresh themselves, and rerun the loop.
        i = region->refresh_out_pos(i);
        j = region->refresh_out_pos(j);
        progress = true;
      }
    }
  }

  // Now we have no instructions in the block containing the IF.
  // Split the IF.
  RegionNode *new_iff = split_thru_region(iff, region);

  // Replace both uses of 'new_iff' with Regions merging True/False
  // paths.  This makes 'new_iff' go dead.
  Node *old_false = nullptr, *old_true = nullptr;
  RegionNode* new_false = nullptr;
  RegionNode* new_true = nullptr;
  for (DUIterator_Last j2min, j2 = iff->last_outs(j2min); j2 >= j2min; --j2) {
    Node *ifp = iff->last_out(j2);
    assert( ifp->Opcode() == Op_IfFalse || ifp->Opcode() == Op_IfTrue, "" );
    ifp->set_req(0, new_iff);
    RegionNode* ifpx = split_thru_region(ifp, region);

    // Replace 'If' projection of a Region with a Region of
    // 'If' projections.
    ifpx->set_req(0, ifpx);       // A TRUE RegionNode

    // Setup dominator info
    set_idom(ifpx, region_dom, dom_depth(region_dom) + 1);

    // Check for splitting loop tails
    if( get_loop(iff)->tail() == ifp )
      get_loop(iff)->_tail = ifpx;

    // Replace in the graph with lazy-update mechanism
    new_iff->set_req(0, new_iff); // hook self so it does not go dead
    lazy_replace(ifp, ifpx);
    new_iff->set_req(0, region);

    // Record bits for later xforms
    if( ifp->Opcode() == Op_IfFalse ) {
      old_false = ifp;
      new_false = ifpx;
    } else {
      old_true = ifp;
      new_true = ifpx;
    }
  }
  _igvn.remove_dead_node(new_iff);
  // Lazy replace IDOM info with the region's dominator
  lazy_replace(iff, region_dom);
  lazy_update(region, region_dom); // idom must be update before handle_uses
  region->set_req(0, nullptr);        // Break the self-cycle. Required for lazy_update to work on region

  // Now make the original merge point go dead, by handling all its uses.
  small_cache region_cache;
  // Preload some control flow in region-cache
  region_cache.lru_insert( new_false, new_false );
  region_cache.lru_insert( new_true , new_true  );
  // Now handle all uses of the splitting block
  for (DUIterator k = region->outs(); region->has_out(k); k++) {
    Node* phi = region->out(k);
    if (!phi->in(0)) {         // Dead phi?  Remove it
      _igvn.remove_dead_node(phi);
    } else if (phi == region) { // Found the self-reference
      continue;                 // No roll-back of DUIterator
    } else if (phi->is_Phi()) { // Expected common case: Phi hanging off of Region
      assert(phi->in(0) == region, "Inconsistent graph");
      // Need a per-def cache.  Phi represents a def, so make a cache
      small_cache phi_cache;

      // Inspect all Phi uses to make the Phi go dead
      for (DUIterator_Last lmin, l = phi->last_outs(lmin); l >= lmin; --l) {
        Node* use = phi->last_out(l);
        // Compute the new DEF for this USE.  New DEF depends on the path
        // taken from the original DEF to the USE.  The new DEF may be some
        // collection of PHI's merging values from different paths.  The Phis
        // inserted depend only on the location of the USE.  We use a
        // 2-element cache to handle multiple uses from the same block.
        handle_use(use, phi, &phi_cache, region_dom, new_false, new_true, old_false, old_true);
      } // End of while phi has uses
      // Remove the dead Phi
      _igvn.remove_dead_node( phi );
    } else {
      assert(phi->in(0) == region, "Inconsistent graph");
      // Random memory op guarded by Region.  Compute new DEF for USE.
      handle_use(phi, region, &region_cache, region_dom, new_false, new_true, old_false, old_true);
    }
    // Every path above deletes a use of the region, except for the region
    // self-cycle (which is needed by handle_use calling find_use_block
    // calling get_ctrl calling get_ctrl_no_update looking for dead
    // regions).  So roll back the DUIterator innards.
    --k;
  } // End of while merge point has phis

  _igvn.remove_dead_node(region);
  if (iff->Opcode() == Op_RangeCheck) {
    // Pin array access nodes: control is updated here to a region. If, after some transformations, only one path
    // into the region is left, an array load could become dependent on a condition that's not a range check for
    // that access. If that condition is replaced by an identical dominating one, then an unpinned load would risk
    // floating above its range check.
    pin_array_access_nodes_dependent_on(new_true);
    pin_array_access_nodes_dependent_on(new_false);
  }

  if (new_false_region != nullptr) {
    *new_false_region = new_false;
  }
  if (new_true_region != nullptr) {
    *new_true_region = new_true;
  }

  DEBUG_ONLY( if (VerifyLoopOptimizations) { verify(); } );
}

void PhaseIdealLoop::pin_array_access_nodes_dependent_on(Node* ctrl) {
  for (DUIterator i = ctrl->outs(); ctrl->has_out(i); i++) {
    Node* use = ctrl->out(i);
    if (!use->depends_only_on_test()) {
      continue;
    }
    Node* pinned_clone = use->pin_array_access_node();
    if (pinned_clone != nullptr) {
      register_new_node_with_ctrl_of(pinned_clone, use);
      _igvn.replace_node(use, pinned_clone);
      --i;
    }
  }
}
