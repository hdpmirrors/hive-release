/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.ql.parse;

import com.google.common.base.Function;
import org.apache.hadoop.hive.ql.plan.PlanUtils;

import java.text.Collator;

/**
 * Statements executed to handle replication have some additional
 * information relevant to the replication subsystem - this class
 * captures those bits of information.
 *
 * Typically, this corresponds to the replicationClause definition
 * in the parser.
 */
public class ReplicationSpec {

  private boolean isInReplicationScope = false; // default is that it's not in a repl scope
  private boolean isMetadataOnly = false; // default is full export/import, not metadata-only
  private String eventId = null;
  private String currStateId = null;
  private boolean isNoop = false;


  public enum KEY {
    REPL_SCOPE("repl.scope"),
    EVENT_ID("repl.event.id"),
    CURR_STATE_ID("repl.last.id"),
    NOOP("repl.noop");

    private final String keyName;

    KEY(String s) {
      this.keyName = s;
    }

    @Override
    public String toString(){
      return keyName;
    }
  }

  static private Collator collator = Collator.getInstance();

  /**
   * Constructor to construct spec based on either the ASTNode that
   * corresponds to the replication clause itself, or corresponds to
   * the parent node, and will scan through the children to instantiate
   * itself.
   * @param node replicationClause node, or parent of replicationClause node
   */
  public ReplicationSpec(ASTNode node){
    if (node != null){
      if (isApplicable(node)){
        init(node);
        return;
      } else {
        for (int i = 1; i < node.getChildCount(); ++i) {
          if (isApplicable((ASTNode) node.getChild(i))) {
            init(node);
            return;
          }
        }
      }
    }
    // If we reached here, we did not find a replication
    // spec in the node or its immediate children. Defaults
    // are to pretend replication is not happening, and the
    // statement above is running as-is.
  }

  /**
   * Default ctor that is useful for determining default states
   */
  public ReplicationSpec(){
    this((ASTNode)null);
  }

  public  ReplicationSpec(
      boolean isInReplicationScope, boolean isMetadataOnly, String eventReplicationState,
      String currentReplicationState, boolean isNoop){
    this.isInReplicationScope = isInReplicationScope;
    this.isMetadataOnly = isMetadataOnly;
    this.eventId = eventReplicationState;
    this.currStateId = currentReplicationState;
    this.isNoop = isNoop;
  }

  public ReplicationSpec(Function<String, String> keyFetcher) {
    // FIXME: test
    String scope = keyFetcher.apply(ReplicationSpec.KEY.REPL_SCOPE.toString());
    this.isMetadataOnly = false;
    this.isInReplicationScope = false;
    if (scope != null){
      if (scope.equalsIgnoreCase("metadata")){
        this.isMetadataOnly = true;
        this.isInReplicationScope = true;
      } else if (scope.equalsIgnoreCase("all")){
        this.isInReplicationScope = true;
      }
    }
    this.eventId = keyFetcher.apply(ReplicationSpec.KEY.EVENT_ID.toString());
    this.currStateId = keyFetcher.apply(ReplicationSpec.KEY.CURR_STATE_ID.toString());
    this.isNoop = Boolean.valueOf(keyFetcher.apply(ReplicationSpec.KEY.NOOP.toString())).booleanValue();
  }

  /**
   * Tests if an ASTNode is a Replication Specification
   * @param node
   * @return
   */
  public static boolean isApplicable(ASTNode node){
    return (node.getToken().getType() == HiveParser.TOK_REPLICATION);
  }

  /**
   * @param currReplState Current object state
   * @param replacementReplState Replacement-candidate state
   * @return whether or not a provided replacement candidate is newer(or equal) to the existing object state or not
   */
  public static boolean allowReplacement(String currReplState, String replacementReplState){
    if (currReplState == null) {
      // if we have no replication state on record for the obj, allow replacement.
      return true;
    }
    if (replacementReplState == null) {
      // if we reached this condition, we had replication state on record for the
      // object, but its replacement has no state. Disallow.
      return false;
    }

    // Lexical comparison according to locale will suffice for now, future might add more logic
    // If oldReplState is less-than or equal to newReplState, allow.
    return (collator.compare(currReplState.toLowerCase(), replacementReplState.toLowerCase()) <= 0);
  }

  private void init(ASTNode node){
    // -> ^(TOK_REPLICATION $replId $isMetadataOnly)
    isInReplicationScope = true;
    eventId = PlanUtils.stripQuotes(node.getChild(0).getText());
    if (node.getChildCount() > 1){
      if (node.getChild(1).getText().toLowerCase().equals("metadata")) {
        isMetadataOnly= true;
      }
    }
  }

  /**
   * @return true if this statement is being run for the purposes of replication
   */
  public boolean isInReplicationScope(){
    return isInReplicationScope;
  }

  /**
   * @return true if this statement refers to metadata-only operation.
   */
  public boolean isMetadataOnly(){
    return isMetadataOnly;
  }

  /**
   * @return the replication state of the event that spawned this statement
   */
  public String getReplicationState() {
    return eventId;
  }

  /**
   * @return the current replication state of the wh
   */
  public String getCurrentReplicationState() {
    return currStateId;
  }

  public void setCurrentReplicationState(String currStateId) {
    this.currStateId = currStateId;
  }

  /**
   * @return whether or not the current replication action should be a noop
   */
  public boolean isNoop() {
    return isNoop;
  }

  /**
   * @param isNoop whether or not the current replication action should be a noop
   */
  public void setNoop(boolean isNoop) {
    this.isNoop = isNoop;
  }

  public String get(KEY key) {
    switch (key){
      case REPL_SCOPE:
        if (isInReplicationScope()){
          if (isMetadataOnly()){
            return "metadata";
          } else {
            return "all";
          }
        } else {
          return "none";
        }
      case EVENT_ID:
        return getReplicationState();
      case CURR_STATE_ID:
        return getCurrentReplicationState();
      case NOOP:
        return String.valueOf(isNoop());
    }
    return null;
  }

}
