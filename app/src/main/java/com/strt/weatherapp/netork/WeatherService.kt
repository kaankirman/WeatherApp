package com.strt.weatherapp.netork

import com.strt.weatherapp.models.WeatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {

    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat:Double,
        @Query("lon") lon:Double,
        @Query("units") units:String?=null,
        @Query("appid") appid:String?=null
    ):Call<WeatherResponse>
}