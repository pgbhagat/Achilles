package info.archinnov.achilles.entity.context;

import info.archinnov.achilles.dao.ThriftCounterDao;
import info.archinnov.achilles.dao.ThriftGenericEntityDao;
import info.archinnov.achilles.dao.ThriftGenericWideRowDao;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import me.prettyprint.hector.api.mutation.Mutator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PersistenceContext
 * 
 * @author DuyHai DOAN
 * 
 */
public class ThriftPersistenceContext extends AchillesPersistenceContext
{
	private static final Logger log = LoggerFactory.getLogger(ThriftPersistenceContext.class);

	private final ThriftDaoContext thriftDaoContext;
	private ThriftGenericEntityDao entityDao;
	private ThriftGenericWideRowDao wideRowDao;
	private ThriftAbstractFlushContext thriftFlushContext;

	public ThriftPersistenceContext(EntityMeta entityMeta, //
			AchillesConfigurationContext configContext, //
			ThriftDaoContext thriftDaoContext, //
			ThriftAbstractFlushContext flushContext, //
			Object entity)
	{
		super(entityMeta, configContext, entity, flushContext);
		log.trace("Create new persistence context for instance {} of class {}", entity,
				entityMeta.getClassName());

		this.thriftDaoContext = thriftDaoContext;
		this.thriftFlushContext = flushContext;
		this.primaryKey = introspector.getKey(entity, entityMeta.getIdMeta());
		this.initDaos();
	}

	public ThriftPersistenceContext(EntityMeta entityMeta, //
			AchillesConfigurationContext configContext, //
			ThriftDaoContext thriftDaoContext, //
			ThriftAbstractFlushContext flushContext, //
			Class<?> entityClass, Object primaryKey)
	{
		super(entityMeta, configContext, entityClass, primaryKey, flushContext);
		log.trace("Create new persistence context for instance {} of class {}", entity,
				entityMeta.getClassName());

		this.thriftDaoContext = thriftDaoContext;
		this.thriftFlushContext = flushContext;
		this.initDaos();
	}

	@Override
	public AchillesPersistenceContext newPersistenceContext(EntityMeta joinMeta, Object joinEntity)
	{
		log.trace("Spawn new persistence context for instance {} of join class {}", joinEntity,
				joinMeta.getClassName());
		return new ThriftPersistenceContext(joinMeta, configContext, thriftDaoContext,
				thriftFlushContext, joinEntity);
	}

	@Override
	public AchillesPersistenceContext newPersistenceContext(Class<?> entityClass,
			EntityMeta joinMeta, Object joinId)
	{
		log.trace("Spawn new persistence context for primary key {} of join class {}", joinId,
				joinMeta.getClassName());

		return new ThriftPersistenceContext(joinMeta, configContext, thriftDaoContext,
				thriftFlushContext, entityClass, joinId);
	}

	public ThriftGenericEntityDao findEntityDao(String columnFamilyName)
	{
		return thriftDaoContext.findEntityDao(columnFamilyName);
	}

	public ThriftGenericWideRowDao findWideRowDao(String columnFamilyName)
	{
		return thriftDaoContext.findWideRowDao(columnFamilyName);
	}

	public ThriftCounterDao getCounterDao()
	{
		return thriftDaoContext.getCounterDao();
	}

	public Mutator<Object> getCurrentColumnFamilyMutator()
	{
		return thriftFlushContext.getWideRowMutator(entityMeta.getTableName());
	}

	public Mutator<Object> getWideRowMutator(String columnFamilyName)
	{
		return thriftFlushContext.getWideRowMutator(columnFamilyName);
	}

	public Mutator<Object> getEntityMutator(String columnFamilyName)
	{
		return thriftFlushContext.getEntityMutator(columnFamilyName);
	}

	public Mutator<Object> getCounterMutator()
	{
		return thriftFlushContext.getCounterMutator();
	}

	public ThriftGenericEntityDao getEntityDao()
	{
		return entityDao;
	}

	public ThriftGenericWideRowDao getColumnFamilyDao()
	{
		return wideRowDao;
	}

	private void initDaos()
	{
		String columnFamilyName = entityMeta.getTableName();
		if (entityMeta.isWideRow())
		{
			this.wideRowDao = thriftDaoContext.findWideRowDao(columnFamilyName);
		}
		else
		{
			this.entityDao = thriftDaoContext.findEntityDao(columnFamilyName);
		}
	}
}
