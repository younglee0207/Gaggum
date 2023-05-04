package com.gaggum

import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.gaggum.databinding.FragmentMainBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.roundToInt

class MainFragment : Fragment() {

    val OPENWEATHER_API_KEY : String = "be002738467412a6651e4278dd3f8c76"
    val FLOWER_API_KEY : String = "XXL2KwjyRGopX8KOKa1BT7gj3em5fLBeqRHJ3xmUpHboTi9da0bZL9fXntQWh63TkQBodm6xHmgeas8K7yBerg=="

    private lateinit var mainActivity: MainActivity
    private lateinit var locationProvider: LocationProvider

    override fun onAttach(context: Context) {
        super.onAttach(context)

        mainActivity = context as MainActivity
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        var lat : String? = null
        var lon : String? = null

        // Inflate the layout for this fragment
        val binding = FragmentMainBinding.inflate(inflater, container, false)

        /* 로그아웃 */
        val logoutBtn = binding.logoutBtn
        logoutBtn.setOnClickListener {
            Firebase.auth.signOut()
            val intent = Intent(requireActivity(), SigninActivity::class.java)
            startActivity(intent)
        }

        fun updateUI() {
            locationProvider = LocationProvider(mainActivity)

            val latitude : Double? = locationProvider.getLocationLatitude()
            val longitude : Double? = locationProvider.getLocationLongitude()

            lat = latitude!!.toString()
            lon = longitude!!.toString()


            if (latitude != null && longitude != null) {
                // 현재 위치 가져오고 UI 업데이트
                val address = getCurrentAddress(latitude, longitude)

                address?.let {
                    binding.mainAddressArea.text = "${it.adminArea}"
                    binding.mainAddressArea2.text = "${it.thoroughfare}"
                }

            } else {
                Toast.makeText(mainActivity, "위도, 경도 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            }

        }

        fun getWeather(lat: String?, lon: String?) {
            val service = WeatherClient.service
            service
                .getWeatherData(lat!!, lon!!, OPENWEATHER_API_KEY)
                .enqueue(object : retrofit2.Callback<WeatherResponseBody> {
                    override fun onResponse(
                        call: Call<WeatherResponseBody>,
                        response: Response<WeatherResponseBody>
                    ) {
                        if (response.isSuccessful) {
                            val icon = response.body()!!.weather[0].icon
                            val avgTemp = (response.body()!!.main.temp - 273.15).roundToInt() * 10 / 10.0

                            binding.mainTempArea.text = "${avgTemp}도"
                            Glide
                                .with(mainActivity)
                                .load("https://openweathermap.org/img/wn/$icon.png")
                                .into(binding.mainWeatherIcon)


                            Log.e("icon", icon.toString())

                        }
                    }

                    override fun onFailure(call: Call<WeatherResponseBody>, t: Throwable) {
                        Toast.makeText(mainActivity, "날씨 정보를 받아오는 데 실패하였습니다.", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        updateUI()
        getWeather(lat, lon)
        getFlower()

        /* ViewPager2 */
//        binding.mainViewPager.adapter = ViewPagerAdapter(getNeedWaterPlantList())
        binding.mainViewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL


        return binding.root
    }

    /* ViewPager Items */
//    private fun getNeedWaterPlantList() : ArrayList<String> {
//        val service = RetrofitObject.service
//        service
//            .getNeedWaterList(1)
//            .enqueue(object : retrofit2.Callback<NeedWaterResponseBody> {
//                override fun onResponse(
//                    call: Call<NeedWaterResponseBody>,
//                    response: Response<NeedWaterResponseBody>
//                ) {
//                    if (response.isSuccessful) {
//                        val res = response.body()!!
//                        Log.e("res", res.toString())
//                    }
//                }
//
//                override fun onFailure(call: Call<NeedWaterResponseBody>, t: Throwable) {
//                    Toast.makeText(mainActivity, "급수 필요한 식물 받아오기 실패", Toast.LENGTH_SHORT).show()
//                }
//
//            })
//
//    }

    /* 날씨 API */

    private fun getCurrentAddress (latitude : Double, longitude : Double) : Address? {
        // 평소에는 getDefault -> 한국은 Korea
        val geoCoder = Geocoder(mainActivity, Locale.KOREA)
        val addresses : List<Address>

        try {
            addresses = geoCoder.getFromLocation(latitude, longitude, 7) as List<Address>
        } catch (ioException : IOException) {
            Toast.makeText(mainActivity, "Geocoder Service 이용이 불가능합니다.", Toast.LENGTH_SHORT).show()
            return null
        } catch (illegalArgumentException : java.lang.IllegalArgumentException) {
            Toast.makeText(mainActivity, "잘못된 위도, 경도입니다.", Toast.LENGTH_SHORT).show()
            return null
        }

        if (addresses == null || addresses.size == 0) {
            Toast.makeText(mainActivity, "주소가 발견되지 않았습니다.", Toast.LENGTH_SHORT).show()
            return null
        }

        val address : Address = addresses[0]
        return address

    }

    private fun getFlower() {
        var cal = Calendar.getInstance()
        var month = (cal.get(Calendar.MONTH) + 1)
        var day = cal.get(Calendar.DATE)

        val service = FlowerClient.service
        service
            .getFlowerData(FLOWER_API_KEY, month, day)
            .enqueue(object : retrofit2.Callback<FlowerResponseBody> {
                override fun onResponse(
                    call: Call<FlowerResponseBody>,
                    response: Response<FlowerResponseBody>
                ) {
                    if (response.isSuccessful) {
                        Toast.makeText(mainActivity, "꽃 가져오기 성공", Toast.LENGTH_SHORT).show()
                        Log.e("response", response.toString())
                    } else {
                        Log.e("실패", "실패")
                    }
                }

                override fun onFailure(call: Call<FlowerResponseBody>, t: Throwable) {
                    Toast.makeText(mainActivity, "Retrofit 실패", Toast.LENGTH_SHORT).show()
                    Log.e("call", call.toString())
                    Log.e("t", t.toString())
                }

            })

    }

}
