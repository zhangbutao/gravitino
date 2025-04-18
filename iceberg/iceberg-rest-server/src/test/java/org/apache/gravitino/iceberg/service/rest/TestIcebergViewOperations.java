/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.gravitino.iceberg.service.rest;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.api.event.IcebergCreateViewEvent;
import org.apache.gravitino.listener.api.event.IcebergCreateViewFailureEvent;
import org.apache.gravitino.listener.api.event.IcebergCreateViewPreEvent;
import org.apache.gravitino.listener.api.event.IcebergDropViewEvent;
import org.apache.gravitino.listener.api.event.IcebergDropViewFailureEvent;
import org.apache.gravitino.listener.api.event.IcebergDropViewPreEvent;
import org.apache.gravitino.listener.api.event.IcebergListViewEvent;
import org.apache.gravitino.listener.api.event.IcebergListViewFailureEvent;
import org.apache.gravitino.listener.api.event.IcebergListViewPreEvent;
import org.apache.gravitino.listener.api.event.IcebergLoadViewEvent;
import org.apache.gravitino.listener.api.event.IcebergLoadViewFailureEvent;
import org.apache.gravitino.listener.api.event.IcebergLoadViewPreEvent;
import org.apache.gravitino.listener.api.event.IcebergRenameViewEvent;
import org.apache.gravitino.listener.api.event.IcebergRenameViewFailureEvent;
import org.apache.gravitino.listener.api.event.IcebergRenameViewPreEvent;
import org.apache.gravitino.listener.api.event.IcebergReplaceViewEvent;
import org.apache.gravitino.listener.api.event.IcebergReplaceViewFailureEvent;
import org.apache.gravitino.listener.api.event.IcebergReplaceViewPreEvent;
import org.apache.gravitino.listener.api.event.IcebergViewExistsEvent;
import org.apache.gravitino.listener.api.event.IcebergViewExistsPreEvent;
import org.apache.iceberg.Schema;
import org.apache.iceberg.UpdateRequirements;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.rest.requests.CreateViewRequest;
import org.apache.iceberg.rest.requests.ImmutableCreateViewRequest;
import org.apache.iceberg.rest.requests.RenameTableRequest;
import org.apache.iceberg.rest.requests.UpdateTableRequest;
import org.apache.iceberg.rest.responses.ListTablesResponse;
import org.apache.iceberg.rest.responses.LoadViewResponse;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.view.ImmutableSQLViewRepresentation;
import org.apache.iceberg.view.ImmutableViewVersion;
import org.apache.iceberg.view.ViewMetadata;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TestIcebergViewOperations extends IcebergNamespaceTestBase {
  private static final Schema viewSchema =
      new Schema(Types.NestedField.of(1, false, "foo_string", Types.StringType.get()));

  private static final Schema newViewSchema =
      new Schema(Types.NestedField.of(2, false, "foo_string1", Types.StringType.get()));

  private static final String VIEW_QUERY = "select 1";

  private DummyEventListener dummyEventListener;

  @Override
  protected Application configure() {
    this.dummyEventListener = new DummyEventListener();
    ResourceConfig resourceConfig =
        IcebergRestTestUtil.getIcebergResourceConfig(
            MockIcebergViewOperations.class, true, Arrays.asList(dummyEventListener));
    // create namespace before each view test
    resourceConfig.register(MockIcebergNamespaceOperations.class);
    resourceConfig.register(MockIcebergViewRenameOperations.class);

    return resourceConfig;
  }

  @ParameterizedTest
  @ValueSource(strings = {"", IcebergRestTestUtil.PREFIX})
  void testListViews(String prefix) {
    setUrlPathWithPrefix(prefix);
    verifyListViewFail(404);
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergListViewPreEvent);
    Assertions.assertTrue(dummyEventListener.popPostEvent() instanceof IcebergListViewFailureEvent);

    verifyCreateNamespaceSucc(IcebergRestTestUtil.TEST_NAMESPACE_NAME);
    verifyCreateViewSucc("list_foo1");
    verifyCreateViewSucc("list_foo2");

    dummyEventListener.clearEvent();
    verifyLisViewSucc(ImmutableSet.of("list_foo1", "list_foo2"));
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergListViewPreEvent);
    Assertions.assertTrue(dummyEventListener.popPostEvent() instanceof IcebergListViewEvent);
  }

  @Test
  void testCreateView() {
    verifyCreateViewFail("create_foo1", 404);
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergCreateViewPreEvent);
    Assertions.assertTrue(
        dummyEventListener.popPostEvent() instanceof IcebergCreateViewFailureEvent);

    verifyCreateNamespaceSucc(IcebergRestTestUtil.TEST_NAMESPACE_NAME);

    verifyCreateViewSucc("create_foo1");
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergCreateViewPreEvent);
    Assertions.assertTrue(dummyEventListener.popPostEvent() instanceof IcebergCreateViewEvent);

    verifyCreateViewFail("create_foo1", 409);
    verifyCreateViewFail("", 400);
  }

  @Test
  void testLoadView() {
    verifyLoadViewFail("load_foo1", 404);
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergLoadViewPreEvent);
    Assertions.assertTrue(dummyEventListener.popPostEvent() instanceof IcebergLoadViewFailureEvent);

    verifyCreateNamespaceSucc(IcebergRestTestUtil.TEST_NAMESPACE_NAME);
    verifyCreateViewSucc("load_foo1");

    dummyEventListener.clearEvent();
    verifyLoadViewSucc("load_foo1");
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergLoadViewPreEvent);
    Assertions.assertTrue(dummyEventListener.popPostEvent() instanceof IcebergLoadViewEvent);

    verifyLoadViewFail("load_foo2", 404);
  }

  @Test
  void testReplaceView() {
    verifyCreateNamespaceSucc(IcebergRestTestUtil.TEST_NAMESPACE_NAME);
    verifyCreateViewSucc("replace_foo1");
    ViewMetadata metadata = getViewMeta("replace_foo1");

    dummyEventListener.clearEvent();
    verifyReplaceSucc("replace_foo1", metadata);
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergReplaceViewPreEvent);
    Assertions.assertTrue(dummyEventListener.popPostEvent() instanceof IcebergReplaceViewEvent);

    verifyDropViewSucc("replace_foo1");

    dummyEventListener.clearEvent();
    verifyUpdateViewFail("replace_foo1", 404, metadata);
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergReplaceViewPreEvent);
    Assertions.assertTrue(
        dummyEventListener.popPostEvent() instanceof IcebergReplaceViewFailureEvent);

    verifyDropNamespaceSucc(IcebergRestTestUtil.TEST_NAMESPACE_NAME);
    verifyUpdateViewFail("replace_foo1", 404, metadata);
  }

  @Test
  void testDropView() {
    verifyDropViewFail("drop_foo1", 404);
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergDropViewPreEvent);
    Assertions.assertTrue(dummyEventListener.popPostEvent() instanceof IcebergDropViewFailureEvent);

    verifyCreateNamespaceSucc(IcebergRestTestUtil.TEST_NAMESPACE_NAME);
    verifyDropViewFail("drop_foo1", 404);

    verifyCreateViewSucc("drop_foo1");

    dummyEventListener.clearEvent();
    verifyDropViewSucc("drop_foo1");
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergDropViewPreEvent);
    Assertions.assertTrue(dummyEventListener.popPostEvent() instanceof IcebergDropViewEvent);

    verifyLoadViewFail("drop_foo1", 404);
  }

  @Test
  void testViewExits() {
    verifyViewExistsStatusCode("exists_foo2", 404);
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergViewExistsPreEvent);
    Event postEvent = dummyEventListener.popPostEvent();
    Assertions.assertTrue(postEvent instanceof IcebergViewExistsEvent);
    Assertions.assertEquals(false, ((IcebergViewExistsEvent) postEvent).isExists());

    verifyCreateNamespaceSucc(IcebergRestTestUtil.TEST_NAMESPACE_NAME);
    verifyViewExistsStatusCode("exists_foo2", 404);

    verifyCreateViewSucc("exists_foo1");
    dummyEventListener.clearEvent();
    verifyViewExistsStatusCode("exists_foo1", 204);
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergViewExistsPreEvent);
    postEvent = dummyEventListener.popPostEvent();
    Assertions.assertTrue(postEvent instanceof IcebergViewExistsEvent);
    Assertions.assertEquals(true, ((IcebergViewExistsEvent) postEvent).isExists());

    verifyLoadViewSucc("exists_foo1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", IcebergRestTestUtil.PREFIX})
  void testRenameTable(String prefix) {
    setUrlPathWithPrefix(prefix);
    // namespace not exits
    verifyRenameViewFail("rename_foo1", "rename_foo3", 404);
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergRenameViewPreEvent);
    Assertions.assertTrue(
        dummyEventListener.popPostEvent() instanceof IcebergRenameViewFailureEvent);

    verifyCreateNamespaceSucc(IcebergRestTestUtil.TEST_NAMESPACE_NAME);
    verifyCreateViewSucc("rename_foo1");

    dummyEventListener.clearEvent();
    // rename
    verifyRenameViewSucc("rename_foo1", "rename_foo2");
    Assertions.assertTrue(dummyEventListener.popPreEvent() instanceof IcebergRenameViewPreEvent);
    Assertions.assertTrue(dummyEventListener.popPostEvent() instanceof IcebergRenameViewEvent);

    verifyLoadViewFail("rename_foo1", 404);
    verifyLoadViewSucc("rename_foo2");

    // source view not exists
    verifyRenameViewFail("rename_foo1", "rename_foo3", 404);

    // dest view exists
    verifyCreateViewSucc("rename_foo3");
    verifyRenameViewFail("rename_foo2", "rename_foo3", 409);
  }

  private Response doCreateView(String name) {
    CreateViewRequest createViewRequest =
        ImmutableCreateViewRequest.builder()
            .name(name)
            .schema(viewSchema)
            .viewVersion(
                ImmutableViewVersion.builder()
                    .versionId(1)
                    .timestampMillis(System.currentTimeMillis())
                    .schemaId(1)
                    .defaultNamespace(Namespace.of(IcebergRestTestUtil.TEST_NAMESPACE_NAME))
                    .addRepresentations(
                        ImmutableSQLViewRepresentation.builder()
                            .sql(VIEW_QUERY)
                            .dialect("spark")
                            .build())
                    .build())
            .build();
    return getViewClientBuilder()
        .post(Entity.entity(createViewRequest, MediaType.APPLICATION_JSON_TYPE));
  }

  private Response doLoadView(String name) {
    return getViewClientBuilder(Optional.of(name)).get();
  }

  private void verifyLoadViewSucc(String name) {
    Response response = doLoadView(name);
    Assertions.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

    LoadViewResponse loadViewResponse = response.readEntity(LoadViewResponse.class);
    Assertions.assertEquals(viewSchema.columns(), loadViewResponse.metadata().schema().columns());
  }

  private void verifyCreateViewFail(String name, int status) {
    Response response = doCreateView(name);
    Assertions.assertEquals(status, response.getStatus());
  }

  private void verifyCreateViewSucc(String name) {
    Response response = doCreateView(name);
    Assertions.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    LoadViewResponse loadViewResponse = response.readEntity(LoadViewResponse.class);
    Schema schema = loadViewResponse.metadata().schema();
    Assertions.assertEquals(schema.columns(), viewSchema.columns());
  }

  private void verifyLoadViewFail(String name, int status) {
    Response response = doLoadView(name);
    Assertions.assertEquals(status, response.getStatus());
  }

  private void verifyReplaceSucc(String name, ViewMetadata base) {
    Response response = doReplaceView(name, base);
    Assertions.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    LoadViewResponse loadViewResponse = response.readEntity(LoadViewResponse.class);
    Assertions.assertEquals(
        newViewSchema.columns(), loadViewResponse.metadata().schema().columns());
  }

  private Response doReplaceView(String name, ViewMetadata base) {
    ViewMetadata.Builder builder =
        ViewMetadata.buildFrom(base).setCurrentVersion(base.currentVersion(), newViewSchema);
    ViewMetadata replacement = builder.build();
    UpdateTableRequest updateTableRequest =
        UpdateTableRequest.create(
            null,
            UpdateRequirements.forReplaceView(base, replacement.changes()),
            replacement.changes());
    return getViewClientBuilder(Optional.of(name))
        .post(Entity.entity(updateTableRequest, MediaType.APPLICATION_JSON_TYPE));
  }

  private ViewMetadata getViewMeta(String viewName) {
    Response response = doLoadView(viewName);
    LoadViewResponse loadViewResponse = response.readEntity(LoadViewResponse.class);
    return loadViewResponse.metadata();
  }

  private void verifyUpdateViewFail(String name, int status, ViewMetadata base) {
    Response response = doReplaceView(name, base);
    Assertions.assertEquals(status, response.getStatus());
  }

  private void verifyDropViewSucc(String name) {
    Response response = doDropView(name);
    Assertions.assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
  }

  private Response doDropView(String name) {
    return getViewClientBuilder(Optional.of(name)).delete();
  }

  private void verifyDropViewFail(String name, int status) {
    Response response = doDropView(name);
    Assertions.assertEquals(status, response.getStatus());
  }

  private void verifyViewExistsStatusCode(String name, int status) {
    Response response = doViewExists(name);
    Assertions.assertEquals(status, response.getStatus());
  }

  private Response doViewExists(String name) {
    return getViewClientBuilder(Optional.of(name)).head();
  }

  private void verifyListViewFail(int status) {
    Response response = doListView();
    Assertions.assertEquals(status, response.getStatus());
  }

  private Response doListView() {
    return getViewClientBuilder().get();
  }

  private void verifyLisViewSucc(Set<String> expectedTableNames) {
    Response response = doListView();
    Assertions.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    ListTablesResponse listTablesResponse = response.readEntity(ListTablesResponse.class);
    Set<String> tableNames =
        listTablesResponse.identifiers().stream()
            .map(identifier -> identifier.name())
            .collect(Collectors.toSet());
    Assertions.assertEquals(expectedTableNames, tableNames);
  }

  private void verifyRenameViewFail(String source, String dest, int status) {
    Response response = doRenameView(source, dest);
    Assertions.assertEquals(status, response.getStatus());
  }

  private Response doRenameView(String source, String dest) {
    RenameTableRequest renameTableRequest =
        RenameTableRequest.builder()
            .withSource(
                TableIdentifier.of(Namespace.of(IcebergRestTestUtil.TEST_NAMESPACE_NAME), source))
            .withDestination(
                TableIdentifier.of(Namespace.of(IcebergRestTestUtil.TEST_NAMESPACE_NAME), dest))
            .build();
    return getRenameViewClientBuilder()
        .post(Entity.entity(renameTableRequest, MediaType.APPLICATION_JSON_TYPE));
  }

  private void verifyRenameViewSucc(String source, String dest) {
    Response response = doRenameView(source, dest);
    Assertions.assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
  }
}
