/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.uaa.identityproviders;

import org.junit.Test;

public final class LdapConfigurationTest {

    @Test(expected = IllegalStateException.class)
    public void noBaseUrl() {
        LdapConfiguration.builder()
            .ldapProfileFile(LdapProfileFile.SimpleBind)
            .ldapGroupFile(LdapGroupFile.NoGroup)
            .build();
    }

    @Test(expected = IllegalStateException.class)
    public void noLdapGroupFile() {
        LdapConfiguration.builder()
            .ldapProfileFile(LdapProfileFile.SimpleBind)
            .baseUrl("test-base-url")
            .build();
    }

    @Test(expected = IllegalStateException.class)
    public void noLdapProfileFile() {
        LdapConfiguration.builder()
            .ldapGroupFile(LdapGroupFile.NoGroup)
            .baseUrl("test-base-url")
            .build();
    }

    @Test
    public void valid() {
        LdapConfiguration.builder()
            .ldapProfileFile(LdapProfileFile.SimpleBind)
            .ldapGroupFile(LdapGroupFile.NoGroup)
            .baseUrl("test-base-url")
            .build();
    }

}