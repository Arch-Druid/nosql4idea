/*
 * Copyright (c) 2015 David Boissier
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

package org.codinjutsu.tools.nosql.database.mongo;

import com.mongodb.*;
import com.mongodb.util.JSON;
import org.apache.commons.io.IOUtils;
import org.codinjutsu.tools.nosql.ServerConfiguration;
import org.codinjutsu.tools.nosql.database.mongo.model.MongoCollection;
import org.codinjutsu.tools.nosql.database.mongo.model.MongoResult;
import org.codinjutsu.tools.nosql.database.mongo.model.MongoQueryOptions;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;


public class MongoManagerTest {

    private MongoManager mongoManager;
    private ServerConfiguration serverConfiguration;


    @Test
    public void loadCollectionsWithEmptyFilter() throws Exception {
        MongoQueryOptions mongoQueryOptions = new MongoQueryOptions();
        mongoQueryOptions.setResultLimit(3);
        MongoResult mongoResult = mongoManager.loadRecords(serverConfiguration, new MongoCollection("dummyCollection", "test"), mongoQueryOptions);
        assertNotNull(mongoResult);
        assertEquals(3, mongoResult.getMongoObjects().size());
    }

    @Test
    public void loadCollectionsWithFilterAndProjection() throws Exception {
        MongoQueryOptions mongoQueryOptions = new MongoQueryOptions();
        mongoQueryOptions.setFilter("{\"label\":\"tata\"}");
        mongoQueryOptions.setProjection("{\"label\":1, \"_id\": 0}");
        mongoQueryOptions.setResultLimit(3);
        MongoResult mongoResult = mongoManager.loadRecords(serverConfiguration, new MongoCollection("dummyCollection", "test"), mongoQueryOptions);
        assertNotNull(mongoResult);
        assertEquals(2, mongoResult.getMongoObjects().size());
        assertEquals("[{ \"label\" : \"tata\"}, { \"label\" : \"tata\"}]", mongoResult.getMongoObjects().toString());
    }

    @Test
    public void loadCollectionsWithFilterAndProjectionAndSortByPrice() throws Exception {
        MongoQueryOptions mongoQueryOptions = new MongoQueryOptions();
        mongoQueryOptions.setFilter("{\"label\":\"tata\"}");
        mongoQueryOptions.setProjection("{\"label\": 1, \"_id\": 0, \"price\": 1}");
        mongoQueryOptions.setSort("{\"price\": 1}");
        mongoQueryOptions.setResultLimit(3);
        MongoResult mongoResult = mongoManager.loadRecords(serverConfiguration, new MongoCollection("dummyCollection", "test"), mongoQueryOptions);
        assertNotNull(mongoResult);
        assertEquals(2, mongoResult.getMongoObjects().size());
        assertEquals("[{ \"label\" : \"tata\" , \"price\" : 10}, { \"label\" : \"tata\" , \"price\" : 15}]", mongoResult.getMongoObjects().toString());
    }

    @Test
    public void updateMongoDocument() throws Exception {
        MongoQueryOptions mongoQueryOptions = new MongoQueryOptions();
        mongoQueryOptions.setFilter("{'label': 'tete'}");
        MongoCollection mongoCollection = new MongoCollection("dummyCollection", "test");
        MongoResult initialData = mongoManager.loadRecords(serverConfiguration, mongoCollection, mongoQueryOptions);
        assertEquals(1, initialData.getMongoObjects().size());
        DBObject initialMongoDocument = initialData.getMongoObjects().get(0);

        initialMongoDocument.put("price", 25);
        mongoManager.update(serverConfiguration, mongoCollection, initialMongoDocument);

        MongoResult updatedResult = mongoManager.loadRecords(serverConfiguration, mongoCollection, mongoQueryOptions);
        List<DBObject> updatedMongoDocuments = updatedResult.getMongoObjects();
        assertEquals(1, updatedMongoDocuments.size());
        DBObject updatedMongoDocument = updatedMongoDocuments.get(0);

        assertEquals(25, updatedMongoDocument.get("price"));
    }

    @Test
    public void deleteMongoDocument() throws Exception {
        MongoQueryOptions mongoQueryOptions = new MongoQueryOptions();
        mongoQueryOptions.setFilter("{'label': 'tete'}");
        MongoCollection mongoCollection = new MongoCollection("dummyCollection", "test");
        MongoResult initialData = mongoManager.loadRecords(serverConfiguration, mongoCollection, mongoQueryOptions);
        assertEquals(1, initialData.getMongoObjects().size());
        DBObject initialMongoDocument = initialData.getMongoObjects().get(0);

        mongoManager.delete(serverConfiguration, mongoCollection, initialMongoDocument.get("_id"));

        MongoResult deleteResult = mongoManager.loadRecords(serverConfiguration, mongoCollection, mongoQueryOptions);
        List<DBObject> updatedMongoDocuments = deleteResult.getMongoObjects();
        assertEquals(0, updatedMongoDocuments.size());
    }

    @Test
    public void loadCollectionsWithAggregateOperators() throws Exception {
        MongoQueryOptions mongoQueryOptions = new MongoQueryOptions();
        mongoQueryOptions.setOperations("[{'$match': {'price': 15}}, {'$project': {'label': 1, 'price': 1}}, {'$group': {'_id': '$label', 'total': {'$sum': '$price'}}}]");
        MongoResult mongoResult = mongoManager.loadRecords(serverConfiguration, new MongoCollection("dummyCollection", "test"), mongoQueryOptions);
        assertNotNull(mongoResult);

        List<DBObject> mongoObjects = mongoResult.getMongoObjects();

        assertEquals(2, mongoObjects.size());
        assertEquals("{ \"_id\" : \"tutu\" , \"total\" : 15}", mongoObjects.get(0).toString());
        assertEquals("{ \"_id\" : \"tata\" , \"total\" : 15}", mongoObjects.get(1).toString());
    }

    @Before
    public void setUp() throws Exception {
        MongoClient mongo = new MongoClient("localhost", 33333);
        DB db = mongo.getDB("test");

        DBCollection dummyCollection = db.getCollection("dummyCollection");
        clearCollection(dummyCollection);
        fillCollectionWithJsonData(dummyCollection, IOUtils.toString(getClass().getResourceAsStream("dummyCollection.json")));

        mongoManager = new MongoManager();
        serverConfiguration = new ServerConfiguration();
        serverConfiguration.setServerUrls(Arrays.asList("localhost:33333"));
    }

    private static void fillCollectionWithJsonData(DBCollection collection, String jsonResource) throws IOException {
        Object jsonParsed = JSON.parse(jsonResource);
        if (jsonParsed instanceof BasicDBList) {
            BasicDBList jsonObject = (BasicDBList) jsonParsed;
            for (Object o : jsonObject) {
                DBObject dbObject = (DBObject) o;
                collection.save(dbObject);
            }
        } else {
            collection.save((DBObject) jsonParsed);
        }
    }

    private static void clearCollection(DBCollection collection) {
        DBCursor cursor = collection.find();
        while (cursor.hasNext()) {
            collection.remove(cursor.next());
        }
    }

}

