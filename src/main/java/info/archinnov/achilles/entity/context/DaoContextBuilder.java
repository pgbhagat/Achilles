package info.archinnov.achilles.entity.context;

import static info.archinnov.achilles.entity.PropertyHelper.isSupportedType;
import static info.archinnov.achilles.serializer.SerializerUtils.*;
import info.archinnov.achilles.consistency.AchillesConsistencyLevelPolicy;
import info.archinnov.achilles.consistency.ThriftConsistencyLevelPolicy;
import info.archinnov.achilles.dao.ThriftCounterDao;
import info.archinnov.achilles.dao.ThriftGenericEntityDao;
import info.archinnov.achilles.dao.ThriftGenericWideRowDao;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.metadata.PropertyMeta;
import info.archinnov.achilles.entity.type.Counter;

import java.util.HashMap;
import java.util.Map;

import me.prettyprint.cassandra.serializers.SerializerTypeInferer;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.Serializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DaoBuilder
 * 
 * @author DuyHai DOAN
 * 
 */
public class DaoContextBuilder
{
	private static final Logger log = LoggerFactory.getLogger(DaoContextBuilder.class);

	private Map<String, ThriftGenericEntityDao<?>> entityDaosMap = new HashMap<String, ThriftGenericEntityDao<?>>();
	private Map<String, ThriftGenericWideRowDao<?, ?>> wideRowDaosMap = new HashMap<String, ThriftGenericWideRowDao<?, ?>>();
	private ThriftCounterDao thriftCounterDao;

	public DaoContext buildDao(Cluster cluster, Keyspace keyspace,
			Map<Class<?>, EntityMeta<?>> entityMetaMap, AchillesConfigurationContext configContext,
			boolean hasSimpleCounter)
	{
		if (hasSimpleCounter)
		{
			thriftCounterDao = new ThriftCounterDao(cluster, keyspace,
					configContext.getConsistencyPolicy());
			log.debug("Build achillesCounterCF dao");
		}

		for (EntityMeta<?> entityMeta : entityMetaMap.values())
		{
			if (!entityMeta.isWideRow())
			{
				buildEntityDao(cluster, keyspace, configContext, entityMeta);
			}

			for (PropertyMeta<?, ?> propertyMeta : entityMeta.getPropertyMetas().values())
			{
				if (propertyMeta.type().isWideMap())
				{
					if (propertyMeta.type().isJoinColumn())
					{
						EntityMeta<?> joinEntityMeta = propertyMeta.getJoinProperties()
								.getEntityMeta();
						buildWideRowDaoForJoinWideMap(cluster, keyspace, configContext,
								propertyMeta, joinEntityMeta);
					}
					else
					{
						buildWideRowDao(cluster, keyspace, configContext, entityMeta, propertyMeta);
					}
				}
			}
		}
		return new DaoContext(entityDaosMap, wideRowDaosMap, thriftCounterDao);
	}

	private <ID> void buildEntityDao(Cluster cluster, Keyspace keyspace,
			AchillesConfigurationContext configContext, EntityMeta<ID> entityMeta)
	{
		String columnFamilyName = entityMeta.getColumnFamilyName();
		Serializer<ID> idSerializer = SerializerTypeInferer.getSerializer(entityMeta.getIdClass());
		ThriftGenericEntityDao<ID> entityDao = new ThriftGenericEntityDao<ID>(//
				cluster, //
				keyspace, //
				idSerializer, //
				columnFamilyName, //
				configContext.getConsistencyPolicy());

		entityDaosMap.put(columnFamilyName, entityDao);
		log.debug("Build entity dao for column family {}", columnFamilyName);
	}

	private <ID, K, V> void buildWideRowDao(Cluster cluster, Keyspace keyspace,
			AchillesConfigurationContext configContext, EntityMeta<ID> entityMeta,
			PropertyMeta<K, V> propertyMeta)
	{
		Class<?> valueClass = propertyMeta.getValueClass();
		ThriftGenericWideRowDao<?, ?> dao;

		String externalCFName = propertyMeta.getExternalCFName();
		AchillesConsistencyLevelPolicy consistencyPolicy = configContext.getConsistencyPolicy();
		Serializer<ID> idSerializer = SerializerTypeInferer.getSerializer(entityMeta.getIdClass());
		Serializer<V> valueSerializer = SerializerTypeInferer.getSerializer(propertyMeta
				.getValueClass());
		if (isSupportedType(valueClass))
		{
			dao = new ThriftGenericWideRowDao<ID, V>(cluster, keyspace, //
					idSerializer, //
					valueSerializer, //
					externalCFName, consistencyPolicy);
		}
		else if (Counter.class.isAssignableFrom(valueClass))
		{
			dao = new ThriftGenericWideRowDao<ID, Long>(cluster, keyspace, //
					idSerializer, //
					LONG_SRZ, //
					externalCFName, consistencyPolicy);
		}
		else
		{
			dao = new ThriftGenericWideRowDao<ID, String>(cluster, keyspace, //
					idSerializer, //
					STRING_SRZ, //
					externalCFName, consistencyPolicy);
		}
		wideRowDaosMap.put(externalCFName, dao);
		log.debug("Build column family dao for wide row {}", externalCFName);

	}

	private <ID, K, V, JOIN_ID> void buildWideRowDaoForJoinWideMap(Cluster cluster,
			Keyspace keyspace, AchillesConfigurationContext configContext,
			PropertyMeta<K, V> propertyMeta, EntityMeta<JOIN_ID> joinEntityMeta)
	{

		Serializer<JOIN_ID> joinIdSerializer = SerializerTypeInferer.getSerializer(joinEntityMeta
				.getIdClass());
		Serializer<ID> idSerializer = SerializerTypeInferer
				.getSerializer(propertyMeta.getIdClass());
		ThriftGenericWideRowDao<ID, JOIN_ID> joinDao = new ThriftGenericWideRowDao<ID, JOIN_ID>(
				cluster, //
				keyspace, //
				idSerializer, //
				joinIdSerializer, //
				propertyMeta.getExternalCFName(),//
				(ThriftConsistencyLevelPolicy) configContext.getConsistencyPolicy());

		log.debug("Building join dao for wide row {}", propertyMeta.getExternalCFName());

		wideRowDaosMap.put(propertyMeta.getExternalCFName(), joinDao);
	}
}