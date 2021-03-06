/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.internal.blobstore;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.common.entity.EntityEvent;
import org.sonatype.nexus.common.entity.EntityMetadata;
import org.sonatype.nexus.orient.OClassNameBuilder;
import org.sonatype.nexus.orient.OIndexNameBuilder;
import org.sonatype.nexus.orient.entity.AttachedEntityMetadata;
import org.sonatype.nexus.orient.entity.IterableEntityAdapter;
import org.sonatype.nexus.orient.entity.action.ReadEntityByPropertyAction;
import org.sonatype.nexus.orient.entity.action.UpdateEntityByPropertyAction;

import com.google.common.annotations.VisibleForTesting;
import com.orientechnologies.orient.core.collate.OCaseInsensitiveCollate;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OClass.INDEX_TYPE;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

/**
 * {@link BlobStoreConfiguration} entity-adapter.
 *
 * since 3.0
 */
@Named
@Singleton
public class BlobStoreConfigurationEntityAdapter
    extends IterableEntityAdapter<BlobStoreConfiguration>
{
  private static final String DB_CLASS = new OClassNameBuilder()
      .prefix("repository")
      .type("blobstore")
      .build();

  private static final String P_NAME = "name";

  private static final String P_TYPE = "type";

  private static final String P_ATTRIBUTES = "attributes";

  private final UpdateEntityByPropertyAction<BlobStoreConfiguration> update =
      new UpdateEntityByPropertyAction<>(this, P_NAME);

  private final ReadEntityByPropertyAction<BlobStoreConfiguration> read =
      new ReadEntityByPropertyAction<>(this, P_NAME);

  @VisibleForTesting
  static final String I_NAME = new OIndexNameBuilder()
      .type(DB_CLASS)
      .property(P_NAME)
      .build();

  public BlobStoreConfigurationEntityAdapter() {
    super(DB_CLASS);
  }

  @Override
  protected void defineType(final OClass type) {
    type.createProperty(P_NAME, OType.STRING)
        .setCollate(new OCaseInsensitiveCollate())
        .setMandatory(true)
        .setNotNull(true);
    type.createProperty(P_TYPE, OType.STRING)
        .setMandatory(true)
        .setNotNull(true);
    type.createProperty(P_ATTRIBUTES, OType.EMBEDDEDMAP)
        .setMandatory(true)
        .setNotNull(true);
    type.createIndex(I_NAME, INDEX_TYPE.UNIQUE, P_NAME);
  }

  @Override
  protected BlobStoreConfiguration newEntity() {
    return new BlobStoreConfiguration();
  }

  @Override
  protected void readFields(final ODocument document, final BlobStoreConfiguration entity) {
    String name = document.field(P_NAME, OType.STRING);
    String type = document.field(P_TYPE, OType.STRING);
    Map<String, Map<String, Object>> attributes = document.field(P_ATTRIBUTES, OType.EMBEDDEDMAP);

    entity.setName(name);
    entity.setType(type);
    entity.setAttributes(detachable(attributes));
  }

  @Override
  protected void writeFields(final ODocument document, final BlobStoreConfiguration entity) {
    document.field(P_NAME, entity.getName());
    document.field(P_TYPE, entity.getType());
    document.field(P_ATTRIBUTES, entity.getAttributes());
  }

  @Override
  public boolean sendEvents() {
    return true;
  }

  @Nullable
  @Override
  public EntityEvent newEvent(final ODocument document, final EventKind eventKind) {
    final EntityMetadata metadata = new AttachedEntityMetadata(this, document);
    final String name = document.field(P_NAME);

    log.trace("newEvent: eventKind: {}, name: {}, metadata: {}", eventKind, name, metadata);
    switch (eventKind) {
      case CREATE:
        return new BlobStoreConfigurationCreatedEvent(metadata, name);
      case UPDATE:
        return new BlobStoreConfigurationUpdatedEvent(metadata, name);
      case DELETE:
        return new BlobStoreConfigurationDeletedEvent(metadata, name);
      default:
        return null;
    }
  }

  /**
   * @since 3.14
   */
  public boolean update(final ODatabaseDocumentTx db, final BlobStoreConfiguration entity) {
    return update.execute(db, entity, entity.getName());
  }

  /**
   * @since 3.14
   */
  public BlobStoreConfiguration getByName(final ODatabaseDocumentTx db, final String name) {
    return read.execute(db, name);
  }

  /**
   * Queries for the groups that a blob store is a part of.
   *
   * @return the {@link BlobStoreConfiguration}s that have a group member matching {@code name}
   *
   * @since 3.14
   */
  public List<BlobStoreConfiguration> getParents(final ODatabaseDocumentTx db, final String name) {
    String query = format("SELECT FROM %s WHERE ? in attributes.group.members", getTypeName());
    List<ODocument> results = db.command(new OSQLSynchQuery<>(query)).execute(name);
    return results.stream().map(this::readEntity).collect(toList());
  }
}
