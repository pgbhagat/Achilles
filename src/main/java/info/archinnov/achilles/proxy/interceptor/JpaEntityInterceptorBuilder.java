package info.archinnov.achilles.proxy.interceptor;

import info.archinnov.achilles.entity.context.AchillesPersistenceContext;
import info.archinnov.achilles.entity.context.ThriftPersistenceContext;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.metadata.PropertyMeta;
import info.archinnov.achilles.validation.Validator;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JpaEntityInterceptorBuilder
 * 
 * @author DuyHai DOAN
 * 
 */
public class JpaEntityInterceptorBuilder<T>
{
	private static final Logger log = LoggerFactory.getLogger(JpaEntityInterceptorBuilder.class);

	private T target;
	private Set<Method> lazyLoaded = new HashSet<Method>();
	private ThriftPersistenceContext context;

	public static <T> JpaEntityInterceptorBuilder<T> builder(AchillesPersistenceContext context,
			T entity)
	{
		return new JpaEntityInterceptorBuilder<T>(context, entity);
	}

	public JpaEntityInterceptorBuilder(AchillesPersistenceContext context, T entity) {
		Validator.validateNotNull(context, "PersistenceContext for interceptor should not be null");
		Validator.validateNotNull(entity, "Target entity for interceptor should not be null");
		this.context = (ThriftPersistenceContext) context;
		this.target = entity;
	}

	public JpaEntityInterceptor<T> build()
	{
		log.debug("Build interceptor for entity of class {}", context
				.getEntityMeta()
				.getClassName());

		JpaEntityInterceptor<T> interceptor = new JpaEntityInterceptor<T>();

		EntityMeta entityMeta = context.getEntityMeta();

		Validator.validateNotNull(target, "Target object for interceptor of '"
				+ context.getEntityClass().getCanonicalName() + "' should not be null");
		Validator.validateNotNull(entityMeta.getGetterMetas(),
				"Getters metadata for interceptor of '"
						+ context.getEntityClass().getCanonicalName() + "' should not be null");
		Validator.validateNotNull(entityMeta.getSetterMetas(),
				"Setters metadata for interceptor of '"
						+ context.getEntityClass().getCanonicalName() + "'should not be null");
		if (entityMeta.isWideRow())
		{
			Validator.validateNotNull(context.getColumnFamilyDao(), "Column Family Dao for '"
					+ context.getEntityClass().getCanonicalName() + "' should not be null");
		}
		else
		{
			Validator.validateNotNull(context.getEntityDao(), "Entity dao for '"
					+ context.getEntityClass().getCanonicalName() + "' should not be null");
		}
		Validator.validateNotNull(entityMeta.getIdMeta(), "Id metadata for '"
				+ context.getEntityClass().getCanonicalName() + "' should not be null");

		interceptor.setTarget(target);
		interceptor.setContext(context);
		interceptor.setGetterMetas(entityMeta.getGetterMetas());
		interceptor.setSetterMetas(entityMeta.getSetterMetas());
		interceptor.setIdGetter(entityMeta.getIdMeta().getGetter());
		interceptor.setIdSetter(entityMeta.getIdMeta().getSetter());
		interceptor.setLazyLoaded(lazyLoaded);
		interceptor.setDirtyMap(new HashMap<Method, PropertyMeta<?, ?>>());
		interceptor.setKey(context.getPrimaryKey());

		return interceptor;
	}
}
