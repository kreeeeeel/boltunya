package ru.aigram.configuration

import org.springframework.beans.factory.ObjectProvider
import ru.aigram.resources.ResourceCatalog

class LazyProviderProbe(val resources: ObjectProvider<ResourceCatalog>)
