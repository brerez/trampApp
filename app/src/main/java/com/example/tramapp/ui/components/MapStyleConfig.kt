package com.example.tramapp.ui.components

import androidx.compose.runtime.Composable
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings

/**
 * Pre-configured map styles for consistent appearance.
 */
object MapStyleConfig {

    /**
     * Premium dark mode style (current app default).
     */
    fun getPremiumDarkMapProperties(): MapProperties {
        return MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = true,
            mapStyleOptions = MapStyleOptions(
                "[" +
                    "  { \"elementType\": \"geometry\", \"stylers\": [ { \"color\": \"#ebe3cd\" } ] }," +
                    "  { \"elementType\": \"labels.text.fill\", \"stylers\": [ { \"color\": \"#523735\" } ] }," +
                    "  { \"elementType\": \"labels.text.stroke\", \"stylers\": [ { \"color\": \"#f5f1e6\" } ] }," +
                    "  { \"featureType\": \"administrative\", \"elementType\": \"geometry.stroke\", \"stylers\": [ { \"color\": \"#c9b2a6\" } ] }," +
                    "  { \"featureType\": \"road\", \"elementType\": \"geometry\", \"stylers\": [ { \"color\": \"#f5f1e6\" } ] }," +
                    "  { \"featureType\": \"water\", \"elementType\": \"geometry.fill\", \"stylers\": [ { \"color\": \"#b9d3c2\" } ] }" +
                    "]"
            )
        )
    }

    /**
     * Minimalist style with reduced visual noise.
     */
    fun getMinimalistMapProperties(): MapProperties {
        return MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = true,
            mapStyleOptions = MapStyleOptions(
                "[" +
                    "  { \"featureType\": \"poi\", \"stylers\": [ { \"visibility\": \"off\" } ] }," +
                    "  { \"featureType\": \"road.polygon\", \"stylers\": [ { \"color\": \"#f5f1e6\" } ] }," +
                    "  { \"featureType\": \"road.stroke\", \"stylers\": [ { \"color\": \"#ffffff\" } ] }" +
                    "]"
            )
        )
    }

    /**
     * High-contrast style for better visibility.
     */
    fun getHighContrastMapProperties(): MapProperties {
        return MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = true,
            mapStyleOptions = MapStyleOptions(
                "[" +
                    "  { \"featureType\": \"poi\", \"stylers\": [ { \"visibility\": \"off\" } ] }," +
                    "  { \"featureType\": \"road.polygon\", \"stylers\": [ { \"color\": \"#e8e4d9\" } ] }," +
                    "  { \"featureType\": \"water\", \"stylers\": [ { \"visibility\": \"on\" }, { \"color\": \"#b9d3c2\" } ] }" +
                    "]"
            )
        )
    }

    /**
     * Default UI settings for the map.
     */
    fun getDefaultMapUiSettings(): MapUiSettings {
        return MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = false
        )
    }

    /**
     * UI settings with custom controls enabled.
     */
    fun getCustomMapUiSettings(): MapUiSettings {
        return MapUiSettings(
            zoomControlsEnabled = true,
            myLocationButtonEnabled = true,
            compassEnabled = true
        )
    }

    /**
     * Combined properties and UI settings for quick use.
     */
    fun getPremiumMapConfig(): Pair<MapProperties, MapUiSettings> {
        return getPremiumDarkMapProperties() to getDefaultMapUiSettings()
    }
}

/**
 * Map configuration builder for flexible customization.
 */
data class MapConfiguration(
    val mapType: MapType = MapType.NORMAL,
    val isMyLocationEnabled: Boolean = true,
    val customStyleOptions: MapStyleOptions? = null,
    val uiSettings: MapUiSettings = MapStyleConfig.getDefaultMapUiSettings()
)

/**
 * Builder for creating map configurations.
 */
class MapConfigurationBuilder {
    private var _mapType: MapType = MapType.NORMAL
    private var _isMyLocationEnabled: Boolean = true
    private var _customStyleOptions: MapStyleOptions? = null
    private var _uiSettings: MapUiSettings = MapStyleConfig.getDefaultMapUiSettings()

    fun withMapType(mapType: MapType): MapConfigurationBuilder {
        this._mapType = mapType
        return this
    }

    fun withMyLocation(enabled: Boolean): MapConfigurationBuilder {
        this._isMyLocationEnabled = enabled
        return this
    }

    fun withCustomStyle(styleOptions: MapStyleOptions): MapConfigurationBuilder {
        this._customStyleOptions = styleOptions
        return this
    }

    fun withUiSettings(uiSettings: MapUiSettings): MapConfigurationBuilder {
        this._uiSettings = uiSettings
        return this
    }

    fun build(): MapConfiguration {
        return MapConfiguration(
            mapType = _mapType,
            isMyLocationEnabled = _isMyLocationEnabled,
            customStyleOptions = _customStyleOptions,
            uiSettings = _uiSettings
        )
    }
}
