package com.example.myadssdk

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ads_sdk.core.AdsManager
import com.example.ads_sdk.domain.model.AdLoadErrorInfo
import com.example.ads_sdk.domain.model.AdEventListener
import com.example.ads_sdk.domain.model.AdType
import com.example.myadssdk.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AdsManager.setAdEventListener(object : AdEventListener {
            override fun onAdLoaded(type: AdType) {
                binding.statusText.text = "$type loaded"
            }

            override fun onAdFailedToLoad(type: AdType, error: AdLoadErrorInfo) {
                binding.statusText.text = "$type failed: ${error.message}"
            }
        })

        AdsManager.loadAds(this)
        AdsManager.loadBanner(binding.bannerContainer)
        AdsManager.loadNative(binding.nativeContainer)

        binding.loadAllButton.setOnClickListener {
            AdsManager.loadAds(this)
            AdsManager.loadBanner(binding.bannerContainer)
            AdsManager.loadNative(binding.nativeContainer)
        }

        binding.showInterstitialButton.setOnClickListener {
            AdsManager.showInterstitial(this)
        }

        binding.showRewardedButton.setOnClickListener {
            AdsManager.showRewarded(this) { reward ->
                Toast.makeText(
                    this,
                    "Reward earned: ${reward.amount} ${reward.type}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.showAppOpenButton.setOnClickListener {
            AdsManager.showAppOpen(this)
        }
    }

    override fun onDestroy() {
        AdsManager.destroyBanner(binding.bannerContainer)
        AdsManager.destroyNative(binding.nativeContainer)
        AdsManager.setAdEventListener(null)
        super.onDestroy()
    }
}
