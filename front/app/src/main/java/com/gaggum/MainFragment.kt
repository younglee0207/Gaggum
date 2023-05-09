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
import androidx.room.Room
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.gaggum.databinding.FragmentMainBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.simpleframework.xml.core.Persister
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
import java.net.URISyntaxException
import java.util.*
import kotlin.math.roundToInt
import kotlin.properties.Delegates

class MainFragment : Fragment() {
//    val onConnect = Emitter.Listener {
//        mSocket.emit("run_mapping","OK")
//    }
    lateinit var mSocket : Socket
    val OPENWEATHER_API_KEY : String = "be002738467412a6651e4278dd3f8c76"
    val FLOWER_API_KEY : String = "XXL2KwjyRGopX8KOKa1BT7gj3em5fLBeqRHJ3xmUpHboTi9da0bZL9fXntQWh63TkQBodm6xHmgeas8K7yBerg=="

    private lateinit var mainActivity: MainActivity
    private lateinit var locationProvider: LocationProvider

    private lateinit var db : ClientDatabase
    private var turtleId : Int = 0
    private lateinit var user : String


    override fun onAttach(context: Context) {
        super.onAttach(context)

        mainActivity = context as MainActivity
        db = Room.databaseBuilder(context, ClientDatabase::class.java, "ClientDatabase")
            .build()
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        user = Firebase.auth.currentUser!!.uid
        Log.e("user", user)

        GlobalScope.launch(Dispatchers.IO) {
            val retrievedUser = db.clientDao().getTurtleId(user)
            if (retrievedUser != null) {
                withContext(Dispatchers.Main) {
                    turtleId = retrievedUser.turtleId
                }
            } else {
                Log.e("실패", "실패하였습니다.")
            }
        }
        // Inflate the layout for this fragment
        val binding = FragmentMainBinding.inflate(inflater, container,false)

        // Set the click listener for the button
        binding.mainMapScanBtn.setOnClickListener {
            try {
                //핸드폰 유선 연결 시 1.핸드폰이랑 노트북 같은 와이파이 쓸 것 2.IPv4 주소를 아래 주소에 입력
                mSocket = IO.socket("https://k8b101.p.ssafy.io")

//            mSocket = IO.socket("http://localhost:3001")

                mSocket.connect()
                Log.d("Connected", "OK")
            } catch (e: URISyntaxException) {
                Log.d("ERR", e.toString())
            }



            mSocket.emit("connectReceive", "OK")
            Log.e("버튼클릭","!!")
        }


        var lat : String? = null
        var lon : String? = null

        // Inflate the layout for this fragment
//        val binding = FragmentMainBinding.inflate(inflater, container, false)

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

        fun getFlower() {
            val cal = Calendar.getInstance()
            val month = (cal.get(Calendar.MONTH) + 1)
            val day = cal.get(Calendar.DATE)

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
                            val res = response.body()?.root?.result?.get(0)
                            val fMonth = res?.fMonth
                            val fDay = res?.fDay
                            val flowImg = res?.imgUrl1
                            val flowName = res?.flowNm
                            val flowLang = res?.flowLang

                            Glide
                                .with(mainActivity)
                                .load(flowImg)
                                .into(binding.todayFlowerImg)

                            binding.todayFlowerTitle.text = "${fMonth}월 ${fDay}일의 추천 꽃"
                            binding.todayFlowerName.text = flowName
                            binding.todayFlowermean.text = flowLang



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

    fun getNeedWaterPlantList() {
        val service = RetrofitObject.service
        service
            .getNeedWaterList(turtleId)
            .enqueue(object : retrofit2.Callback<NeedWaterResponseBody> {
                override fun onResponse(
                    call: Call<NeedWaterResponseBody>,
                    response: Response<NeedWaterResponseBody>
                ) {
                    if (response.isSuccessful) {
                        val viewPager = binding.mainViewPager
                        viewPager.adapter = ViewPagerAdapter(response.body()!!.data, mainActivity)
                        viewPager.setPageTransformer(ZoomOutPageTransformer())

                        // 여백 너비 정의
                        val pageMarginPx = resources.getDimensionPixelOffset(R.dimen.pageMargin)
                        val pagerWidth = resources.getDimensionPixelOffset(R.dimen.pageWidth)
                        val screnWidth = resources.displayMetrics.widthPixels
                        val offsetPx = screnWidth - pageMarginPx - pagerWidth

                        viewPager.setPageTransformer { page, position ->
                            page.translationX = position * -offsetPx
                        }

                        viewPager.offscreenPageLimit = 3

                        Log.e("response", response.body()!!.data.toString())

                    }
                }

                override fun onFailure(call: Call<NeedWaterResponseBody>, t: Throwable) {
                    Toast.makeText(mainActivity, "급수 필요한 식물 받아오기 실패", Toast.LENGTH_SHORT).show()
                }

            })
    }
        updateUI()
        getWeather(lat, lon)
        getFlower()


        /* ViewPager2 */
        getNeedWaterPlantList()


        return binding.root
    }

    inner class ZoomOutPageTransformer : ViewPager2.PageTransformer {

        private val MIN_SCALE = 0.85f
        private val MIN_ALPHA = 0.5f
        override fun transformPage(view: View, position: Float) {
            view.apply {
                val pageWidth = width
                val pageHeight = height
                when {
                    position < -1 -> { // [-Infinity,-1)
                        // This page is way off-screen to the left.
                        alpha = 0f
                    }
                    position <= 1 -> { // [-1,1]
                        // Modify the default slide transition to shrink the page as well
                        val scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position))
                        val vertMargin = pageHeight * (1 - scaleFactor) / 2
                        val horzMargin = pageWidth * (1 - scaleFactor) / 2
                        translationX = if (position < 0) {
                            horzMargin - vertMargin / 2
                        } else {
                            horzMargin + vertMargin / 2
                        }

                        // Scale the page down (between MIN_SCALE and 1)
                        scaleX = scaleFactor
                        scaleY = scaleFactor

                        // Fade the page relative to its size.
                        alpha = (MIN_ALPHA +
                                (((scaleFactor - MIN_SCALE) / (1 - MIN_SCALE)) * (1 - MIN_ALPHA)))
                    }
                    else -> { // (1,+Infinity]
                        // This page is way off-screen to the right.
                        alpha = 0f
                    }
                }
            }
        }
    }
    /* ViewPager Items */

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

}
