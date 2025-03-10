/*
 * Copyright (c) 2020 vitasystems GmbH and Hannover Medical School.
 *
 * This file is part of project openEHR_SDK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ehrbase.openehr.sdk.aql.dto.select;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

public class SelectClause {

    private boolean isDistinct = false;

    private List<SelectExpression> statement;

    public List<SelectExpression> getStatement() {
        return this.statement;
    }

    public void setStatement(List<SelectExpression> statement) {
        this.statement = statement;
    }

    @JsonProperty(index = 10)
    public boolean isDistinct() {
        return isDistinct;
    }

    public void setDistinct(boolean distinct) {
        isDistinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SelectClause that = (SelectClause) o;
        return isDistinct == that.isDistinct && Objects.equals(statement, that.statement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isDistinct, statement);
    }

    @Override
    public String toString() {
        return "SelectClause{" + "isDistinct=" + isDistinct + ", statement=" + statement + '}';
    }
}
