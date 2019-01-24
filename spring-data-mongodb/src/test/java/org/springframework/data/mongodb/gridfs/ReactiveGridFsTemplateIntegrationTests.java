/*
 * Copyright 2019 the original author or authors.
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
package org.springframework.data.mongodb.gridfs;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.core.query.Query.*;
import static org.springframework.data.mongodb.gridfs.GridFsCriteria.*;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;

import org.bson.BsonObjectId;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StreamUtils;

import com.mongodb.reactivestreams.client.gridfs.AsyncInputStream;
import com.mongodb.reactivestreams.client.gridfs.helpers.AsyncStreamHelper;

/**
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration("classpath:gridfs/reactive-gridfs.xml")
public class ReactiveGridFsTemplateIntegrationTests {

	Resource resource = new ClassPathResource("gridfs/gridfs.xml");

	@Autowired ReactiveGridFsOperations operations;

	@Before
	public void setUp() {
		operations.delete(new Query()) //
				.as(StepVerifier::create) //
				.verifyComplete();
	}

	@Test // DATAMONGO-1855
	public void storesAndFindsSimpleDocument() {

		DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
		DefaultDataBuffer first = factory.wrap("first".getBytes());
		DefaultDataBuffer second = factory.wrap("second".getBytes());

		ObjectId reference = operations.store(Flux.just(first, second), "foo.xml").block();

		operations.find(query(where("_id").is(reference))) //
				.as(StepVerifier::create) //
				.assertNext(actual -> {
					assertThat(((BsonObjectId) actual.getId()).getValue()).isEqualTo(reference);
				}).verifyComplete();
	}

	@Test // DATAMONGO-1855
	public void writesMetadataCorrectly() throws IOException {

		Document metadata = new Document("key", "value");

		AsyncInputStream stream = AsyncStreamHelper.toAsyncInputStream(resource.getInputStream());

		ObjectId reference = operations.store(stream, "foo.xml", "binary/octet-stream", metadata).block();

		operations.find(query(whereMetaData("key").is("value"))) //
				.as(StepVerifier::create) //
				.consumeNextWith(actual -> {
					assertThat(actual.getObjectId()).isEqualTo(reference);
				})//
				.verifyComplete();
	}

	@Test // DATAMONGO-1855
	public void marshalsComplexMetadata() throws IOException {

		Metadata metadata = new Metadata();
		metadata.version = "1.0";

		AsyncInputStream stream = AsyncStreamHelper.toAsyncInputStream(resource.getInputStream());

		ObjectId reference = operations.store(stream, "foo.xml", "binary/octet-stream", metadata).block();

		operations.find(query(whereMetaData("version").is("1.0"))) //
				.as(StepVerifier::create) //
				.consumeNextWith(actual -> {
					assertThat(actual.getObjectId()).isEqualTo(reference);
				})//
				.verifyComplete();
	}

	@Test // DATAMONGO-1855
	public void getResourceShouldRetrieveContentByIdentity() throws IOException {

		byte[] content = StreamUtils.copyToByteArray(resource.getInputStream());
		AsyncInputStream upload = AsyncStreamHelper.toAsyncInputStream(resource.getInputStream());

		ObjectId reference = operations.store(upload, "foo.xml", null, null).block();

		operations.findOne(query(where("_id").is(reference))).flatMap(operations::getResource)
				.flatMapMany(ReactiveGridFsResource::getDownloadStream) //
				.transform(DataBufferUtils::join).as(StepVerifier::create) //
				.consumeNextWith(dataBuffer -> {

					byte[] actual = new byte[dataBuffer.readableByteCount()];
					dataBuffer.read(actual);

					assertThat(actual).isEqualTo(content);
				}).verifyComplete();
	}

	static class Metadata {
		String version;
	}
}
