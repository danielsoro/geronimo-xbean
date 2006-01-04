/**
 *
 * Copyright 2005 (C) The original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xbean.jmx;

import javax.management.MBeanInfo;
import javax.management.ObjectName;


/**
 * @version $Revision: $ $Date: $
 */
public class DummyMBeanInfoGenerator implements MBeanInfoGenerator {
    public MBeanInfo createMBeanInfo(Object bean, ObjectName objectName) {
        return new MBeanInfo(bean.getClass().getName(), "dummy", null, null, null, null);
    }
}