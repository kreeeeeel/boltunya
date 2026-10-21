package ru.aigram.configuration

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import java.util.function.Supplier

inline fun <reified T : Any> testBeanProvider(noinline supplier: () -> T): ObjectProvider<T> {
    val factory = DefaultListableBeanFactory()
    val definition = RootBeanDefinition(T::class.java)
    definition.instanceSupplier = Supplier(supplier)
    definition.isLazyInit = true
    factory.registerBeanDefinition("testDependency", definition)

    return factory.getBeanProvider(T::class.java)
}
