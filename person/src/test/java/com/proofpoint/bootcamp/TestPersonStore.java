/*
 * Copyright 2010 Proofpoint, Inc.
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
package com.proofpoint.bootcamp;

import com.google.common.collect.ImmutableList;
import com.proofpoint.event.client.InMemoryEventClient;
import com.proofpoint.units.Duration;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static com.proofpoint.bootcamp.PersonEvent.personAdded;
import static com.proofpoint.bootcamp.PersonEvent.personRemoved;
import static com.proofpoint.bootcamp.PersonEvent.personUpdated;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestPersonStore
{
    @Test
    public void testStartsEmpty()
    {
        PersonStore store = new PersonStore(new StoreConfig(), new InMemoryEventClient());
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    public void testTtl()
            throws InterruptedException
    {
        StoreConfig config = new StoreConfig();
        config.setTtl(new Duration(1, TimeUnit.MILLISECONDS));

        PersonStore store = new PersonStore(config, new InMemoryEventClient());
        store.put("foo", new Person("foo@example.com", "Mr Foo", "Mr Foo"));
        Thread.sleep(2);
        Assert.assertNull(store.get("foo"));
    }

    @Test
    public void testPut()
    {
        InMemoryEventClient eventClient = new InMemoryEventClient();
        PersonStore store = new PersonStore(new StoreConfig(), eventClient);
        store.put("foo", new Person("foo@example.com", "Mr Foo", "Mr Foo"));

        assertEquals(new Person("foo@example.com", "Mr Foo", "Mr Foo"), store.get("foo"));
        assertEquals(store.getAll().size(), 1);

        assertEquals(eventClient.getEvents(), ImmutableList.of(personAdded("foo", new Person("foo@example.com", "Mr Foo", "Mr Foo"))));
    }

    @Test
    public void testIdempotentPut()
    {
        InMemoryEventClient eventClient = new InMemoryEventClient();
        PersonStore store = new PersonStore(new StoreConfig(), eventClient);
        store.put("foo", new Person("foo@example.com", "Mr Foo", "Mr Foo"));
        store.put("foo", new Person("foo@example.com", "Mr Bar", "Mr Bar"));

        assertEquals(new Person("foo@example.com", "Mr Bar", "Mr Bar"), store.get("foo"));
        assertEquals(store.getAll().size(), 1);

        assertEquals(eventClient.getEvents(), ImmutableList.of(
                personAdded("foo", new Person("foo@example.com", "Mr Foo", "Mr Foo")),
                personUpdated("foo", new Person("foo@example.com", "Mr Bar", "Mr Bar"))
        ));
    }

    @Test
    public void testDelete()
    {
        InMemoryEventClient eventClient = new InMemoryEventClient();
        PersonStore store = new PersonStore(new StoreConfig(), eventClient);
        store.put("foo", new Person("foo@example.com", "Mr Foo", "Mr Foo"));
        store.delete("foo");

        assertNull(store.get("foo"));
        assertTrue(store.getAll().isEmpty());

        assertEquals(eventClient.getEvents(), ImmutableList.of(
                personAdded("foo", new Person("foo@example.com", "Mr Foo", "Mr Foo")),
                personRemoved("foo", new Person("foo@example.com", "Mr Foo", "Mr Foo"))
        ));
    }

    @Test
    public void testIdempotentDelete()
    {
        InMemoryEventClient eventClient = new InMemoryEventClient();
        PersonStore store = new PersonStore(new StoreConfig(), eventClient);
        store.put("foo", new Person("foo@example.com", "Mr Foo", "Mr Foo"));

        store.delete("foo");
        assertTrue(store.getAll().isEmpty());
        assertNull(store.get("foo"));

        store.delete("foo");
        assertTrue(store.getAll().isEmpty());
        assertNull(store.get("foo"));

        assertEquals(eventClient.getEvents(), ImmutableList.of(
                personAdded("foo", new Person("foo@example.com", "Mr Foo", "Mr Foo")),
                personRemoved("foo", new Person("foo@example.com", "Mr Foo", "Mr Foo"))
        ));
    }

    @Test
    public void testGetAll()
    {
        PersonStore store = new PersonStore(new StoreConfig(), new InMemoryEventClient());

        store.put("foo", new Person("foo@example.com", "Mr Foo", "Mr Foo"));
        store.put("bar", new Person("bar@example.com", "Mr Bar", "Mr Foo"));

        assertEquals(store.getAll().size(), 2);
        assertEquals(store.getAll(), asList(new Person("foo@example.com", "Mr Foo", "Mr Foo"), new Person("bar@example.com", "Mr Bar", "Mr Bar")));
    }

    //test to see if store only holds 5
    @Test
    public void testMax5()
    {
        PersonStore store = new PersonStore(new StoreConfig(), new InMemoryEventClient());

        store.put( "0", new Person(  "0@email.com", "name0", "id0" ));
        store.put( "1", new Person(  "1@email.com", "name1", "id1" ));
        store.put( "2", new Person(  "2@email.com", "name2", "id2" ));
        store.put( "3", new Person(  "3@email.com", "name3", "id3" ));
        store.put( "4", new Person(  "4@email.com", "name4", "id4" ));
        assertEquals(new Person("0@email.com", "name0", "0"), store.get("0"));

        store.put( "5", new Person(  "5@email.com", "name5", "id5" ));
        assertEquals(store.getAll().size(), 5);
        assertNull(store.get("0"));
        assertEquals(new Person("5@email.com", "name5", "5"), store.get("5"));
    }
}
