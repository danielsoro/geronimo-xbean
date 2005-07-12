/**
 *
 * Copyright 2005 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.gbean.kernel.simple;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.gbean.kernel.runtime.ServiceInstance;
import org.gbean.kernel.ServiceAlreadyExistsException;
import org.gbean.kernel.ServiceNotFoundException;

/**
 * @version $Rev$ $Date$
 */
public class SimpleRegistry {
    private final Map registry = new HashMap();
    private final Map domainIndex = new HashMap();
    private String defaultDomainName;

    public SimpleRegistry(String defaultDomainName) {
        this.defaultDomainName = defaultDomainName;
    }

    public void start() {
    }

    public void stop() {
        synchronized (this) {
            registry.clear();
            domainIndex.clear();
        }
    }

    public boolean isRegistered(ObjectName name) {
        synchronized (this) {
            return registry.containsKey(name);
        }
    }

    public void register(ObjectName objectName, ServiceInstance serviceInstance) throws ServiceAlreadyExistsException {
        // do as much work as possible outside of the synchronized block
        ObjectName name = serviceInstance.getObjectName();
        String domainName = name.getDomain();
        if (domainName.length() == 0) {
            domainName = defaultDomainName;
        }

        // convert properties list to a HashMap as it is more efficient then the synchronized Hashtable
        Map properties = new HashMap(name.getKeyPropertyList());

        synchronized (this) {
            registry.put(name, serviceInstance);

            Map nameToProperties = (Map) domainIndex.get(domainName);
            if (nameToProperties == null) {
                nameToProperties = new HashMap();
                domainIndex.put(domainName, nameToProperties);
            }
            nameToProperties.put(name, properties);
        }
    }

    public void unregister(ObjectName name) throws ServiceNotFoundException {
        String domainName = name.getDomain();
        synchronized (this) {
            registry.remove(name);

            // just leave the an empty nameToProperty map
            Map nameToProperties = (Map) domainIndex.get(domainName);
            if (nameToProperties != null) {
                nameToProperties.remove(name);
            }
        }
    }

    public ServiceInstance getServiceInstance(ObjectName name) throws ServiceNotFoundException {
        ServiceInstance serviceInstance;
        synchronized (this) {
            serviceInstance = (ServiceInstance) registry.get(name);
        }
        if (serviceInstance == null) {
            throw new ServiceNotFoundException(name.getCanonicalName());
        }
        return serviceInstance;
    }

    public Set listServiceInstances(ObjectName pattern) {
        Set serviceNames = listServiceNames(pattern);
        HashSet serviceInstances = new HashSet(serviceNames.size());
        synchronized (this) {
            for (Iterator iterator = serviceNames.iterator(); iterator.hasNext();) {
                ObjectName objectName = (ObjectName) iterator.next();
                ServiceInstance serviceInstance = (ServiceInstance) registry.get(objectName);
                if (serviceInstance != null) {
                    serviceInstances.add(serviceInstance);
                }
            }
        }
        return serviceInstances;
    }

    public Set listServiceNames(ObjectName pattern) {
        if (pattern == null) {
            synchronized (this) {
                return new HashSet(registry.keySet());
            }
        }

        String patternDomain = pattern.getDomain();
        if (patternDomain.length() == 0) {
            patternDomain = defaultDomainName;
        }

        // work with a copy of the registry key set
        List nameToProperties;
        if (!pattern.isDomainPattern()) {
            synchronized (this) {
                // create an array list big enough to match all names... extra space is better than resizing
                nameToProperties = new ArrayList(registry.size());

                // find we are only matching one specific domain, so
                // just grab it directly from the index
                Map map = (Map) domainIndex.get(patternDomain);
                if (map != null) {
                    nameToProperties.addAll(map.entrySet());
                }
            }
        } else if (patternDomain.equals("*")) {
            // this
            //  is very commmon, so support it directly
            synchronized (this) {
                // create an array list big enough to match all names... extra space is better than resizing
                nameToProperties = new ArrayList(registry.size());

                // find we are matching all domain, so just grab all of them directly
                for (Iterator iterator = domainIndex.values().iterator(); iterator.hasNext();) {
                    Map map = (Map) iterator.next();

                    // we can just copy the entry set directly into the list we don't
                    // have to worry about duplicates as the maps are mutually exclusive
                    nameToProperties.addAll(map.entrySet());
                }
            }
        } else {
            String perl5Pattern = domainPatternToPerl5(patternDomain);
            Pattern domainPattern = Pattern.compile(perl5Pattern);

            synchronized (this) {
                // create an array list big enough to match all names... extra space is better than resizing
                nameToProperties = new ArrayList(registry.size());

                // find all of the matching domains
                for (Iterator iterator = domainIndex.entrySet().iterator(); iterator.hasNext();) {
                    Map.Entry entry = (Map.Entry) iterator.next();
                    String domain = (String) entry.getKey();
                    if (domainPattern.matcher(domain).matches()) {
                        // we can just copy the entry set directly into the list we don't
                        // have to worry about duplicates as the maps are mutually exclusive
                        Map map = (Map) entry.getValue();
                        nameToProperties.addAll(map.entrySet());
                    }
                }
            }
        }

        if (nameToProperties.isEmpty()) {
            return Collections.EMPTY_SET;
        }

        // convert the pattern property list to a HashMap as it is not synchronized
        Map patternProperties = new HashMap(pattern.getKeyPropertyList());
        patternProperties.remove("*");
        boolean isMatchAll = patternProperties.isEmpty();
        boolean isPropertyPattern = pattern.isPropertyPattern();

        Set matchingNames = new HashSet();
        for (Iterator iterator = nameToProperties.iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            Map properties = (Map) entry.getValue();

            if (isMatchAll) {
                matchingNames.add(entry.getKey());
            } else if (isPropertyPattern) {
                if (properties.entrySet().containsAll(patternProperties.entrySet())) {
                    matchingNames.add(entry.getKey());
                }
            } else {
                if (properties.entrySet().equals(patternProperties.entrySet())) {
                    matchingNames.add(entry.getKey());
                }
            }
        }
        return matchingNames;
    }

    private static String domainPatternToPerl5(String pattern) {
        char[] patternCharacters = pattern.toCharArray();
        StringBuffer buffer = new StringBuffer(2 * patternCharacters.length);
        for (int position = 0; position < patternCharacters.length; position++) {
            char character = patternCharacters[position];
            switch (character) {
                case '*':
                    // replace '*' with '.*'
                    buffer.append(".*");
                    break;
                case '?':
                    // replace '?' with '.'
                    buffer.append('.');
                    break;
                default:
                    // escape any perl5 characters with '\'
                    if (isPerl5MetaCharacter(character)) {
                        buffer.append('\\');
                    }
                    buffer.append(character);
                    break;
            }
        }

        return buffer.toString();
    }

    private static boolean isPerl5MetaCharacter(char character) {
        return ("'*?+[]()|^$.{}\\".indexOf(character) >= 0);
    }
}