package io.canavar.vakit.location

import io.canavar.vakit.data.LocationFix

interface LocationSource {
    suspend fun current(): LocationFix?
}
