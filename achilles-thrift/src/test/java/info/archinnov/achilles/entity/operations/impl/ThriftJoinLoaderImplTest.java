/**
 *
 * Copyright (C) 2012-2013 DuyHai DOAN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.archinnov.achilles.entity.operations.impl;

import static info.archinnov.achilles.entity.metadata.PropertyType.*;
import static info.archinnov.achilles.serializer.ThriftSerializerUtils.*;
import static me.prettyprint.hector.api.beans.AbstractComposite.ComponentEquality.*;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import info.archinnov.achilles.composite.ThriftCompositeFactory;
import info.archinnov.achilles.consistency.ThriftConsistencyLevelPolicy;
import info.archinnov.achilles.context.ThriftImmediateFlushContext;
import info.archinnov.achilles.context.ThriftPersistenceContext;
import info.archinnov.achilles.dao.ThriftCounterDao;
import info.archinnov.achilles.dao.ThriftGenericEntityDao;
import info.archinnov.achilles.entity.context.ThriftPersistenceContextTestBuilder;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.metadata.PropertyMeta;
import info.archinnov.achilles.entity.metadata.PropertyType;
import info.archinnov.achilles.entity.metadata.transcoding.DataTranscoder;
import info.archinnov.achilles.entity.operations.ThriftJoinEntityLoader;
import info.archinnov.achilles.test.builders.CompleteBeanTestBuilder;
import info.archinnov.achilles.test.builders.PropertyMetaTestBuilder;
import info.archinnov.achilles.test.mapping.entity.CompleteBean;
import info.archinnov.achilles.test.mapping.entity.UserBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.prettyprint.hector.api.beans.Composite;
import me.prettyprint.hector.api.mutation.Mutator;

import org.apache.cassandra.utils.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.ImmutableMap;

@RunWith(MockitoJUnitRunner.class)
public class ThriftJoinLoaderImplTest {

	@InjectMocks
	private ThriftJoinLoaderImpl thriftJoinLoader;

	@Mock
	private ThriftJoinEntityLoader joinHelper;

	@Mock
	private ThriftCompositeFactory thriftCompositeFactory;

	@Mock
	private EntityMeta entityMeta;

	@Mock
	private ThriftGenericEntityDao entityDao;

	@Mock
	private ThriftCounterDao thriftCounterDao;

	@Mock
	private Mutator<Object> mutator;

	@Mock
	private ThriftConsistencyLevelPolicy policy;

	@Mock
	private Map<String, ThriftGenericEntityDao> entityDaosMap;

	@Mock
	private ThriftImmediateFlushContext thriftImmediateFlushContext;

	@Mock
	private ThriftGenericEntityDao joinEntityDao;

	@Mock
	private DataTranscoder transcoder;

	private CompleteBean entity = CompleteBeanTestBuilder.builder().randomId()
			.buid();

	private ThriftPersistenceContext context;

	@Captor
	private ArgumentCaptor<List<Object>> listCaptor;

	@Before
	public void setUp() {
		context = ThriftPersistenceContextTestBuilder
				.context(entityMeta, thriftCounterDao, policy,
						CompleteBean.class, entity.getId()).entity(entity)
				.thriftImmediateFlushContext(thriftImmediateFlushContext)
				.entityDao(entityDao).entityDaosMap(entityDaosMap).build();
		when(entityMeta.getTableName()).thenReturn("cf");
		when(thriftImmediateFlushContext.getEntityMutator("cf")).thenReturn(
				mutator);
		when(entityDaosMap.get("join_cf")).thenReturn(joinEntityDao);

	}

	@Test
	public void should_load_join_list() throws Exception {

		PropertyMeta joinIdMeta = PropertyMetaTestBuilder
				.valueClass(Long.class).type(ID).transcoder(transcoder).build();

		EntityMeta joinMeta = new EntityMeta();
		joinMeta.setTableName("join_cf");
		joinMeta.setIdMeta(joinIdMeta);

		PropertyMeta propertyMeta = PropertyMetaTestBuilder
				.valueClass(UserBean.class).type(JOIN_LIST).joinMeta(joinMeta)
				.field("friends").transcoder(transcoder).build();

		Composite start = new Composite();
		Composite end = new Composite();
		start.addComponent(JOIN_LIST.flag(), BYTE_SRZ);
		start.addComponent("friends", STRING_SRZ);
		start.addComponent("0", STRING_SRZ);

		end.addComponent(JOIN_LIST.flag(), BYTE_SRZ);
		end.addComponent("friends", STRING_SRZ);
		end.addComponent("1", STRING_SRZ);

		when(thriftCompositeFactory.createBaseForQuery(propertyMeta, EQUAL))
				.thenReturn(start);
		when(
				thriftCompositeFactory.createBaseForQuery(propertyMeta,
						GREATER_THAN_EQUAL)).thenReturn(end);

		List<Pair<Composite, Object>> columns = new ArrayList<Pair<Composite, Object>>();
		columns.add(Pair.<Composite, Object> create(start, "11"));
		columns.add(Pair.<Composite, Object> create(end, "12"));
		when(
				entityDao.findColumnsRange(entity.getId(), start, end, false,
						Integer.MAX_VALUE)).thenReturn(columns);
		when(transcoder.forceDecodeFromJSON("11", Long.class)).thenReturn(11L);
		when(transcoder.forceDecodeFromJSON("12", Long.class)).thenReturn(12L);

		UserBean user1 = new UserBean();
		UserBean user2 = new UserBean();
		Map<Object, Object> joinEntitiesMap = ImmutableMap.<Object, Object> of(
				11L, user1, 12L, user2);
		when(
				joinHelper.loadJoinEntities(eq(UserBean.class),
						listCaptor.capture(), eq(joinMeta), eq(joinEntityDao)))
				.thenReturn(joinEntitiesMap);

		List<Object> actual = thriftJoinLoader.loadJoinListProperty(context,
				propertyMeta);

		assertThat(actual).containsExactly(user1, user2);
		assertThat(listCaptor.getValue()).containsExactly(11L, 12L);
	}

	@Test
	public void should_load_join_set() throws Exception {
		EntityMeta joinMeta = new EntityMeta();
		PropertyMeta joinIdMeta = PropertyMetaTestBuilder
				.valueClass(Long.class).transcoder(transcoder)
				.type(PropertyType.ID).build();

		joinMeta.setIdMeta(joinIdMeta);
		joinMeta.setTableName("join_cf");

		PropertyMeta propertyMeta = PropertyMetaTestBuilder
				.valueClass(UserBean.class).type(JOIN_SET).joinMeta(joinMeta)
				.field("followers").transcoder(transcoder).build();

		Composite start = new Composite();
		Composite end = new Composite();

		start.addComponent(JOIN_SET.flag(), BYTE_SRZ);
		start.addComponent("followers", STRING_SRZ);
		start.addComponent("11", STRING_SRZ);

		end.addComponent(JOIN_SET.flag(), BYTE_SRZ);
		end.addComponent("followers", STRING_SRZ);
		end.addComponent("12", STRING_SRZ);

		when(thriftCompositeFactory.createBaseForQuery(propertyMeta, EQUAL))
				.thenReturn(start);
		when(
				thriftCompositeFactory.createBaseForQuery(propertyMeta,
						GREATER_THAN_EQUAL)).thenReturn(end);

		List<Pair<Composite, Object>> columns = new ArrayList<Pair<Composite, Object>>();
		columns.add(Pair.<Composite, Object> create(start, ""));
		columns.add(Pair.<Composite, Object> create(end, ""));
		when(
				entityDao.findColumnsRange(entity.getId(), start, end, false,
						Integer.MAX_VALUE)).thenReturn(columns);

		UserBean user1 = new UserBean();
		UserBean user2 = new UserBean();
		Map<Object, Object> joinEntitiesMap = ImmutableMap.<Object, Object> of(
				11L, user1, 12L, user2);

		when(transcoder.forceDecodeFromJSON("11", Long.class)).thenReturn(11L);
		when(transcoder.forceDecodeFromJSON("12", Long.class)).thenReturn(12L);

		when(
				joinHelper.loadJoinEntities(eq(UserBean.class),
						listCaptor.capture(), eq(joinMeta), eq(joinEntityDao)))
				.thenReturn(joinEntitiesMap);

		Set<Object> actual = thriftJoinLoader.loadJoinSetProperty(context,
				propertyMeta);

		assertThat(actual).contains(user1, user2);
		assertThat(listCaptor.getValue()).containsExactly(11L, 12L);
	}

	@Test
	public void should_load_join_map() throws Exception {
		EntityMeta joinMeta = new EntityMeta();
		PropertyMeta joinIdMeta = PropertyMetaTestBuilder
				.valueClass(Long.class).transcoder(transcoder)
				.type(PropertyType.ID).build();

		joinMeta.setIdMeta(joinIdMeta);
		joinMeta.setTableName("join_cf");

		PropertyMeta propertyMeta = PropertyMetaTestBuilder
				.keyValueClass(Integer.class, UserBean.class).type(JOIN_MAP)
				.joinMeta(joinMeta).field("preferences").transcoder(transcoder)
				.build();

		Composite start = new Composite();
		Composite end = new Composite();

		start.addComponent(JOIN_MAP.flag(), BYTE_SRZ);
		start.addComponent("preferences", STRING_SRZ);
		start.addComponent("1", STRING_SRZ);

		end.addComponent(JOIN_MAP.flag(), BYTE_SRZ);
		end.addComponent("preferences", STRING_SRZ);
		end.addComponent("2", STRING_SRZ);

		when(thriftCompositeFactory.createBaseForQuery(propertyMeta, EQUAL))
				.thenReturn(start);
		when(
				thriftCompositeFactory.createBaseForQuery(propertyMeta,
						GREATER_THAN_EQUAL)).thenReturn(end);

		List<Pair<Composite, Object>> columns = new ArrayList<Pair<Composite, Object>>();
		columns.add(Pair.<Composite, Object> create(start, "11"));
		columns.add(Pair.<Composite, Object> create(end, "12"));

		when(transcoder.forceDecodeFromJSON("1", Integer.class)).thenReturn(1);
		when(transcoder.forceDecodeFromJSON("2", Integer.class)).thenReturn(2);
		when(transcoder.forceDecodeFromJSON("11", Long.class)).thenReturn(11L);
		when(transcoder.forceDecodeFromJSON("12", Long.class)).thenReturn(12L);

		when(
				entityDao.findColumnsRange(entity.getId(), start, end, false,
						Integer.MAX_VALUE)).thenReturn(columns);

		UserBean user1 = new UserBean();
		UserBean user2 = new UserBean();
		Map<Object, Object> joinEntitiesMap = ImmutableMap.<Object, Object> of(
				11L, user1, 12L, user2);
		when(
				joinHelper.loadJoinEntities(eq(UserBean.class),
						listCaptor.capture(), eq(joinMeta), eq(joinEntityDao)))
				.thenReturn(joinEntitiesMap);

		Map<Object, Object> actual = thriftJoinLoader.loadJoinMapProperty(
				context, propertyMeta);

		assertThat(actual.get(1)).isSameAs(user1);
		assertThat(actual.get(2)).isSameAs(user2);
		assertThat(listCaptor.getValue()).containsExactly(11L, 12L);
	}
}
