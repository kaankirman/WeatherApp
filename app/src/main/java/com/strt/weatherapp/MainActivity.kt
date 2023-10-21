package com.strt.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.strt.weatherapp.databinding.ActivityMainBinding
import com.strt.weatherapp.models.WeatherResponse
import com.strt.weatherapp.netork.WeatherService
import kotlinx.coroutines.Delay
import kotlinx.coroutines.delay
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.sql.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient:FusedLocationProviderClient
    private var mProgressDialog:Dialog?=null
    private lateinit var binding:ActivityMainBinding
    private lateinit var mSharedPreferences: SharedPreferences

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivRefresh.setOnClickListener{
            val intent = Intent(this@MainActivity,MainActivity::class.java)
            startActivity(intent)
            Toast.makeText(this, "Data Refreshed", Toast.LENGTH_SHORT).show()

        }

        mFusedLocationClient=LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences=getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)
        setupUI()

        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "your location provider is turned off, please turn it on",
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withActivity(this@MainActivity).withPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).withListener(
                object : MultiplePermissionsListener {
                    @RequiresApi(Build.VERSION_CODES.S)
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        if (report.areAllPermissionsGranted()){
                            requestLocationData()
                        }
                        if (report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(
                                this@MainActivity,
                                "you have denied loaction permissions",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread().check()
        }


    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest= com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority=com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(mLocationRequest,mLocationCallback, Looper.myLooper())
    }

    private val mLocationCallback=object :LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation:Location=locationResult.lastLocation
            val latitude=mLastLocation.latitude
            Log.i("Current Latitude","$latitude")
            val longitude=mLastLocation.longitude
            Log.i("Current longitude","$longitude")
            getLocationWeatherDetails(latitude,longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if (Constants.isNetworkAvailable(this)){
            val retrofit:Retrofit=Retrofit.Builder().baseUrl(Constants.BASE_URL).addConverterFactory(GsonConverterFactory.create()).build()
            val service:WeatherService=retrofit.create(WeatherService::class.java)
            val listCall:Call<WeatherResponse> = service.getWeather(latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID)
            showCustomProgressDialog()
            listCall.enqueue(object:Callback<WeatherResponse>{
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful){
                        hideProgressDialog()
                        val weatherList: WeatherResponse? =response.body()
                        if (weatherList != null) {
                            val weatherResponseJsonString=Gson().toJson(weatherList)
                            val editor=mSharedPreferences.edit()
                            editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                            editor.apply()
                            setupUI()
                        }
                        Log.i("response result","$weatherList")
                    }else{
                        val rc=response.code()
                        when(rc){
                            400 -> {Log.e("Error 400","bad connection")}
                            404 -> {Log.e("error 404","Not Found")}
                            else -> {Log.e("Error","generic error")}
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errorrrrrr", t.message.toString())
                    hideProgressDialog()
                }

            })
        }else{
            Toast.makeText(this, "no connection", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this).setMessage("it looks like you have turned off permissions").setPositiveButton("Go to settings"){
            _,_-> try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri=Uri.fromParts("package",packageName,null)
                intent.data=uri
                startActivity(intent)
            }catch (e:ActivityNotFoundException){
                e.printStackTrace()
            }
        }.setNegativeButton("Cancel"){dialog,_->dialog.dismiss()}.show()
    }

    private fun isLocationEnabled():Boolean{
        val locationManager:LocationManager=getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    }

    private fun showCustomProgressDialog(){
        mProgressDialog= Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()

    }
    private fun hideProgressDialog(){
        if (mProgressDialog!=null)
            mProgressDialog!!.dismiss()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupUI(){
        val weatherResponseJsonString=mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")
        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList =Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)
        for (i in weatherList.weather.indices){
            Log.i("weather name",weatherList.weather.toString())
            binding.tvMain.text=weatherList.weather[i].main
            binding.tvMainDescription.text=weatherList.weather[i].description
            binding.tvTemperature.text=weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
            binding.tvSunrise.text=unixTime(weatherList.sys.sunrise)
            binding.tvSunset.text=unixTime(weatherList.sys.sunset)
            binding.tvTemperatureMax.text=weatherList.main.temp_max.toString()+ getUnit(application.resources.configuration.locales.toString())
            binding.tvTemperatureMin.text=weatherList.main.temp_min.toString()+ getUnit(application.resources.configuration.locales.toString())
            binding.tvWindDegree.text=weatherList.wind.deg.toString() + "°"
            binding.tvWindMiles.text=weatherList.wind.speed.toString()+"m/hr"
            binding.tvLocation.text=weatherList.name+"/"+weatherList.sys.country
            binding.tvHumidtyPercentage.text=weatherList.main.humidity.toString()+"%"

            when(weatherList.weather[i].icon){
                "01d"->binding.ivMain.setImageResource(R.drawable.sunny)
                "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)
            }

        }}
    }

    private fun getUnit(value:String):String?{
        var value="°C"
        if ("US"==value||"LR"==value||"MM"==value)
            value="°F"
        return value
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun unixTime(timex:Long):String{
        val date= Date(timex*1000L)
        val sdf=SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone= TimeZone.getDefault()
        return sdf.format(date)
    }

}