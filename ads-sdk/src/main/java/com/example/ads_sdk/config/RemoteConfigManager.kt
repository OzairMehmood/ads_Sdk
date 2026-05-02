package com.example.ads_sdk.config

/** In-memory config holder that can be fed by Firebase Remote Config or a backend. */
object RemoteConfigManager {

    @Volatile
    private var config: AdsConfig = AdsConfig()

    fun init(defaultConfig: AdsConfig = AdsConfig()) {
        config = defaultConfig
    }

    fun updateConfig(remoteConfig: AdsConfig) {
        config = remoteConfig
    }

    fun updateConfig(block: (AdsConfig) -> AdsConfig) {
        config = block(config)
    }

    fun getConfig(): AdsConfig = config
}
