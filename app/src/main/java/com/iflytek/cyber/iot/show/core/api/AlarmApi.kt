package com.iflytek.cyber.iot.show.core.api

import com.iflytek.cyber.iot.show.core.model.Alert
import com.iflytek.cyber.iot.show.core.model.AlertBody
import com.iflytek.cyber.iot.show.core.model.Message
import retrofit2.Call
import retrofit2.http.*

interface AlarmApi {

    @GET("alerts")
    fun getAlerts(): Call<ArrayList<Alert>>

    @PUT("alerts/{id}")
    fun updateAlert(@Path("id") id: String, @Body body: AlertBody): Call<Message>

    @POST("alerts")
    fun addNewAlarm(@Body body: AlertBody): Call<Message>

    @DELETE("alerts/{id}")
    fun deleteAlarm(@Path("id") id: String): Call<Message>
}