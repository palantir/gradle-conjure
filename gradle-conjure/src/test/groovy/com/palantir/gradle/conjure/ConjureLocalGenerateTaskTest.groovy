/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.conjure;

import spock.lang.Specification;

class ConjureLocalGenerateTaskTest extends Specification {

    def "correctly parses product metadata"() {
        when:
        def productMetadata = ConjureLocalGenerateTask.parseProductNameAndVersion(fileName)

        then:
        productMetadata.getV1() == expectedProductName
        productMetadata.getV2().getValue() == expectedProductVersion

        where:
        fileName                           | expectedProductName | expectedProductVersion
        'foo-1.0.0.json'                   | "foo"               | '1.0.0'
        "foo-baz-2-1.0.0.json"             | "foo-baz-2"         | "1.0.0"
        "foo-1.0.0.conjure.json"           | "foo"               | "1.0.0"
        "foo-1.0.0-rc1.conjure.json"       | "foo"               | "1.0.0-rc1"
        "foo-1.0.0-12-gabcd.conjure.json"  | "foo"               | "1.0.0-12-gabcd"
    }

    def "fails to parse invalid names"() {
        when:
        ConjureLocalGenerateTask.parseProductNameAndVersion(fileName)

        then:
        RuntimeException ex = thrown()
        ex.message.contains(fileName)

        where:
        fileName                      | _
        "invalid-name.json"           | _
        "invalid-version-1.x.0.json"  | _
        "invalid-structure-1.2.0.tgz" | _
    }
}
