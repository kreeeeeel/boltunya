package ru.aigram.configuration

import jakarta.inject.Inject
import jakarta.inject.Provider
import ru.aigram.resources.ResourceCatalog

class LazyProviderProbe @Inject constructor(val resources: Provider<ResourceCatalog>)
