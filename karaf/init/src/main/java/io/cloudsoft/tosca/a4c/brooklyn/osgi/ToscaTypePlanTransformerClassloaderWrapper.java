package io.cloudsoft.tosca.a4c.brooklyn.osgi;

import io.cloudsoft.tosca.a4c.brooklyn.plan.ToscaTypePlanTransformer;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredTypeLoadingContext;
import org.apache.brooklyn.core.typereg.BrooklynTypePlanTransformer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * The purpose of this class is to set the thread context classloader upon all calls to
 * a delegate {@link ToscaTypePlanTransformer}.  This is done so that Spring lookups
 * using org.springframework.util.ClassUtils#getDefaultClassLoader() will find the
 * classloader for this bundle, rather than whatever may happen to be in place at the
 * point of call.
 *
 * Note that the subclassing of ToscaTypePlanTransformer is only required so that
 * a bean of this type can be injected into the {@link io.cloudsoft.tosca.a4c.brooklyn.ToscaEntitySpecResolver},
 * which needs the {@link #createApplicationSpecFromTopologyId}.
 */
public class ToscaTypePlanTransformerClassloaderWrapper extends ToscaTypePlanTransformer implements BrooklynTypePlanTransformer {

    ToscaTypePlanTransformer delegateTransformer;

    public ToscaTypePlanTransformerClassloaderWrapper(ToscaTypePlanTransformer toscaTypePlanTransformer) {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(toscaClassLoader());
            delegateTransformer = toscaTypePlanTransformer;
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Override
    public String getFormatCode() {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(toscaClassLoader());
            return delegateTransformer.getFormatCode();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Override
    public String getFormatName() {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(toscaClassLoader());
            return delegateTransformer.getFormatName();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Override
    public String getFormatDescription() {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(toscaClassLoader());
            return delegateTransformer.getFormatDescription();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Override
    public double scoreForType(@Nonnull RegisteredType type, @Nonnull RegisteredTypeLoadingContext context) {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(toscaClassLoader());
            return delegateTransformer.scoreForType(type, context);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Nullable
    @Override
    public Object create(@Nonnull RegisteredType type, @Nonnull RegisteredTypeLoadingContext context) {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(toscaClassLoader());
            return delegateTransformer.create(type, context);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Override
    public double scoreForTypeDefinition(String formatCode, Object catalogData) {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(toscaClassLoader());
            return delegateTransformer.scoreForTypeDefinition(formatCode, catalogData);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Override
    public List<RegisteredType> createFromTypeDefinition(String formatCode, Object catalogData) {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(toscaClassLoader());
            return delegateTransformer.createFromTypeDefinition(formatCode, catalogData);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Override
    public void setManagementContext(ManagementContext managementContext) {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(toscaClassLoader());
            delegateTransformer.setManagementContext(managementContext);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Override
    public EntitySpec<? extends Application> createApplicationSpecFromTopologyId(String id) {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(toscaClassLoader());
            return delegateTransformer.createApplicationSpecFromTopologyId(id);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    public AbstractBrooklynObjectSpec<?, ?> createSpec(RegisteredType type, RegisteredTypeLoadingContext context) throws Exception {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(toscaClassLoader());
            return delegateTransformer.createSpec(type, context);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private ClassLoader toscaClassLoader() {
        return ToscaTypePlanTransformer.class.getClassLoader();
    }
}
