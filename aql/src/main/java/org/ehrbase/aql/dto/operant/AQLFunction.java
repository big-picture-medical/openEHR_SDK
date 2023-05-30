/*
 * Copyright (c) 2022 vitasystems GmbH and Hannover Medical School.
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
package org.ehrbase.aql.dto.operant;

/**
 * @author Stefan Spiska
 */
public enum AQLFunction {
    COUNT(AQLFunctionType.AGGREGATE),
    MIN(AQLFunctionType.AGGREGATE),
    MAX(AQLFunctionType.AGGREGATE),
    AVG(AQLFunctionType.AGGREGATE);

    private final AQLFunctionType functionType;

    AQLFunction(AQLFunctionType functionType) {
        this.functionType = functionType;
    }

    public AQLFunctionType getFunctionType() {
        return functionType;
    }
}
