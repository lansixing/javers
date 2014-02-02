package org.javers.core;

import com.google.gson.TypeAdapter;
import org.javers.common.pico.JaversModule;
import org.javers.common.validation.Validate;
import org.javers.core.configuration.JaversCoreConfiguration;
import org.javers.core.diff.changetype.Value;
import org.javers.core.json.JsonConverterBuilder;
import org.javers.core.json.JsonTypeAdapter;
import org.javers.core.metamodel.property.*;
import org.javers.core.pico.CoreJaversModule;
import org.javers.core.pico.ManagedClassFactoryModule;
import org.javers.core.metamodel.type.TypeMapper;
import org.javers.core.metamodel.type.ValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates JaVers instance based on your domain model metadata and custom configuration.
 * <br/>
 * Supports two configuring methods:
 * <ul>
 *     <li/>by properties file, see {TBA ...}
 *     <li/>programmatically using builder style methods
 * </ul>
 *
 * @author bartosz walacik
 */
public class JaversBuilder extends AbstractJaversBuilder {
    private static final Logger logger = LoggerFactory.getLogger(JaversBuilder.class);

    private Set<ManagedClassDefinition> managedClassDefinitions = new HashSet<>();
    private List<JaversModule> externalModules = new ArrayList<>();

    private JaversBuilder() {
        logger.debug("starting up javers ...");

        // bootstrap phase 1: core beans
        bootContainer(new CoreJaversModule());
    }

    public static JaversBuilder javers() {
        return new JaversBuilder();
    }

    public Javers build() {

        // bootstrap phase 2: JSON beans
        bootJsonConverter();

        // bootstrap phase 3:
        // ManagedClassFactory & managed classes registration
        bootManagedClasses();

        logger.info("javers instance is up & ready");
        return getContainerComponent(Javers.class);
    }

    /**
     * registers {@link org.javers.core.metamodel.property.Entity} with id-property selected on the basis of @Id annotation
     */
    public JaversBuilder registerEntity(Class<?> entityClass) {
        Validate.argumentIsNotNull(entityClass);
        return registerEntity( new EntityDefinition(entityClass));
    }

    /**
     * registers {@link org.javers.core.metamodel.property.Entity} with id-property selected explicitly by name
     */
    public JaversBuilder registerEntity(Class<?> entityClass, String idPropertyName) {
        Validate.argumentsAreNotNull(entityClass, idPropertyName);
        return registerEntity( new EntityDefinition(entityClass, idPropertyName) );
    }

    private JaversBuilder registerEntity(EntityDefinition entityDefinition) {
        managedClassDefinitions.add(entityDefinition);
        return this;
    }

    /**
     * registers {@link org.javers.core.metamodel.property.ValueObject}
     */
    public JaversBuilder registerValueObject(Class<?> valueObjectClass) {
        Validate.argumentIsNotNull(valueObjectClass);
        managedClassDefinitions.add(new ValueObjectDefinition(valueObjectClass));
        return this;
    }

    public JaversBuilder registerValueObjects(Class<?>...valueObjectClasses) {
        for(Class clazz : valueObjectClasses) {
            registerValueObject(clazz);
        }
        return this;
    }

    /**
     * registers {@link ValueType}
     */
    public JaversBuilder registerValue(Class<?> valueClass) {
        Validate.argumentIsNotNull(valueClass);
        typeMapper().registerValueType(valueClass);
        return this;
    }

    /**
     * Registers {@link ValueType} and its custom JSON adapter.
     * <p/>
     *
     * Useful for not trivial ValueTypes when Gson's default representation isn't appropriate
     *
     * @see JsonTypeAdapter
     * @see JsonTypeAdapter#getValueType()
     */
    public JaversBuilder registerValueTypeAdapter(JsonTypeAdapter typeAdapter) {
        registerValue(typeAdapter.getValueType());
        jsonConverterBuilder().registerJsonTypeAdapter(typeAdapter);
        return this;
    }

    /**
     * Registers {@link ValueType} and its custom native
     *  <a href="http://code.google.com/p/google-gson/">Gson</a> adapter.
     * <p/>
     *
     * Useful when you already have Gson {@link TypeAdapter}s implemented.
     *
     * @see TypeAdapter
     */
    public JaversBuilder registerValueGsonTypeAdapter(Class valueType, TypeAdapter nativeAdapter) {
        registerValue(valueType);
        jsonConverterBuilder().registerNativeTypeAdapter(valueType, nativeAdapter);
        return this;
    }

    /**
     * Switch on when you need type safe {@link Value}s
     * serialization stored in polymorfic collections like List, List&lt;Object&gt;, Map&lt;Object,Object&gt;, etc.
     *
     * @see org.javers.core.json.JsonConverterBuilder#typeSafeValues(boolean)
     */
    public JaversBuilder typeSafeValues(){
        jsonConverterBuilder().typeSafeValues(true);
        return this;
    }

    public JaversBuilder registerEntities(Class<?>...entityClasses) {
        for(Class clazz : entityClasses) {
            registerEntity(clazz);
        }
        return this;
    }

    /**
     * {@link MappingStyle#FIELD} by default
     */
    public JaversBuilder withMappingStyle(MappingStyle mappingStyle) {
        Validate.argumentIsNotNull(mappingStyle);
        coreConfiguration().withMappingStyle(mappingStyle);
        return this;
    }

   /* @Deprecated
    public JaversBuilder addModule(JaversModule javersModule) {
        Validate.argumentIsNotNull(javersModule);
        externalModules.add(javersModule);
        return this;
    }*/

    private void registerManagedClasses() {
        TypeMapper typeMapper = typeMapper();
        for (ManagedClassDefinition def : managedClassDefinitions) {
            if (def instanceof ValueObjectDefinition) {
                ValueObject valueObject = managedClassFactory().create((ValueObjectDefinition)def);
                typeMapper.registerValueObjectType(valueObject);
            }
            if (def instanceof EntityDefinition) {
                Entity entity = managedClassFactory().create((EntityDefinition)def);
                typeMapper.registerEntityType(entity);
            }
        }
    }

    private ManagedClassFactory managedClassFactory() {
        return getContainerComponent(ManagedClassFactory.class);
    }

    private TypeMapper typeMapper() {
        return getContainerComponent(TypeMapper.class);
    }

    private JaversCoreConfiguration coreConfiguration() {
        return getContainerComponent(JaversCoreConfiguration.class);
    }

    private JsonConverterBuilder jsonConverterBuilder(){
        return getContainerComponent(JsonConverterBuilder.class);
    }

    private void bootManagedClasses() {
        addModule(new ManagedClassFactoryModule(coreConfiguration()));
        registerManagedClasses();
    }

    private void bootJsonConverter() {
        addComponent(jsonConverterBuilder().build());
    }
}
