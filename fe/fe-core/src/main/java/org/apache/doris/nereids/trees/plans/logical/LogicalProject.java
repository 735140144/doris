// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.nereids.trees.plans.logical;

import org.apache.doris.nereids.analyzer.Unbound;
import org.apache.doris.nereids.analyzer.UnboundStar;
import org.apache.doris.nereids.memo.GroupExpression;
import org.apache.doris.nereids.properties.LogicalProperties;
import org.apache.doris.nereids.trees.expressions.BoundStar;
import org.apache.doris.nereids.trees.expressions.Expression;
import org.apache.doris.nereids.trees.expressions.NamedExpression;
import org.apache.doris.nereids.trees.expressions.Slot;
import org.apache.doris.nereids.trees.plans.Plan;
import org.apache.doris.nereids.trees.plans.PlanType;
import org.apache.doris.nereids.trees.plans.algebra.Project;
import org.apache.doris.nereids.trees.plans.visitor.PlanVisitor;
import org.apache.doris.nereids.util.ExpressionUtils;
import org.apache.doris.nereids.util.Utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Logical project plan.
 */
public class LogicalProject<CHILD_TYPE extends Plan> extends LogicalUnary<CHILD_TYPE>
        implements Project, OutputPrunable {

    private final List<NamedExpression> projects;
    private final List<NamedExpression> excepts;
    private final boolean isDistinct;

    public LogicalProject(List<NamedExpression> projects, CHILD_TYPE child) {
        this(projects, ImmutableList.of(), false, child);
    }

    /**
     * only for test.
     */
    public LogicalProject(List<NamedExpression> projects, List<NamedExpression> excepts, CHILD_TYPE child) {
        this(projects, excepts, false, child);
    }

    public LogicalProject(List<NamedExpression> projects, boolean isDistinct, CHILD_TYPE child) {
        this(projects, ImmutableList.of(), isDistinct, child);
    }

    public LogicalProject(List<NamedExpression> projects, List<NamedExpression> excepts,
            boolean isDistinct, CHILD_TYPE child) {
        this(projects, excepts, isDistinct, Optional.empty(), Optional.empty(), child);
    }

    private LogicalProject(List<NamedExpression> projects, List<NamedExpression> excepts, boolean isDistinct,
            Optional<GroupExpression> groupExpression, Optional<LogicalProperties> logicalProperties,
            CHILD_TYPE child) {
        super(PlanType.LOGICAL_PROJECT, groupExpression, logicalProperties, child);
        Preconditions.checkArgument(projects != null, "projects can not be null");
        // only ColumnPrune rule may produce empty projects, this happens in rewrite phase
        // so if projects is empty, all plans have been bound already.
        Preconditions.checkArgument(!projects.isEmpty() || !(child instanceof Unbound),
                "projects can not be empty when child plan is unbound");
        this.projects = projects.isEmpty()
                ? ImmutableList.of(ExpressionUtils.selectMinimumColumn(child.getOutput()))
                : projects;
        this.excepts = ImmutableList.copyOf(excepts);
        this.isDistinct = isDistinct;
    }

    /**
     * Get project list.
     *
     * @return all project of this node.
     */
    @Override
    public List<NamedExpression> getProjects() {
        return projects;
    }

    public List<NamedExpression> getExcepts() {
        return excepts;
    }

    public boolean isAllSlots() {
        return projects.stream().allMatch(NamedExpression::isSlot);
    }

    @Override
    public List<Slot> computeOutput() {
        return projects.stream()
                .map(NamedExpression::toSlot)
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public String toString() {
        return Utils.toSqlString("LogicalProject[" + id.asInt() + "]",
                "distinct", isDistinct,
                "projects", projects,
                "excepts", excepts
        );
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitLogicalProject(this, context);
    }

    @Override
    public List<? extends Expression> getExpressions() {
        return projects;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LogicalProject<?> that = (LogicalProject<?>) o;
        boolean equal = projects.equals(that.projects)
                && excepts.equals(that.excepts)
                && isDistinct == that.isDistinct;
        // TODO: should add exprId for UnBoundStar and BoundStar for equality comparison
        if (!projects.isEmpty() && (projects.get(0) instanceof UnboundStar || projects.get(0) instanceof BoundStar)) {
            equal = child().getLogicalProperties().equals(that.child().getLogicalProperties());
        }
        return equal;
    }

    @Override
    public int hashCode() {
        return Objects.hash(projects);
    }

    @Override
    public LogicalProject<Plan> withChildren(List<Plan> children) {
        Preconditions.checkArgument(children.size() == 1);
        return new LogicalProject<>(projects, excepts, isDistinct, children.get(0));
    }

    @Override
    public LogicalProject<Plan> withGroupExpression(Optional<GroupExpression> groupExpression) {
        return new LogicalProject<>(projects, excepts, isDistinct,
                groupExpression, Optional.of(getLogicalProperties()), child());
    }

    @Override
    public Plan withGroupExprLogicalPropChildren(Optional<GroupExpression> groupExpression,
            Optional<LogicalProperties> logicalProperties, List<Plan> children) {
        Preconditions.checkArgument(children.size() == 1);
        return new LogicalProject<>(projects, excepts, isDistinct,
                groupExpression, logicalProperties, children.get(0));
    }

    public LogicalProject<Plan> withProjects(List<NamedExpression> projects) {
        return new LogicalProject<>(projects, excepts, isDistinct, child());
    }

    public LogicalProject<Plan> withProjectsAndChild(List<NamedExpression> projects, Plan child) {
        return new LogicalProject<>(projects, excepts, isDistinct, child);
    }

    public boolean isDistinct() {
        return isDistinct;
    }

    @Override
    public List<NamedExpression> getOutputs() {
        return projects;
    }

    @Override
    public Plan pruneOutputs(List<NamedExpression> prunedOutputs) {
        return withProjects(prunedOutputs);
    }

    @Override
    public JSONObject toJson() {
        JSONObject logicalProject = super.toJson();
        JSONObject properties = new JSONObject();
        properties.put("Projects", projects.toString());
        properties.put("Excepts", excepts.toString());
        properties.put("IsDistinct", isDistinct);
        logicalProject.put("Properties", properties);
        return logicalProject;
    }
}
