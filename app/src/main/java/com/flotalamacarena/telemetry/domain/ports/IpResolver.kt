package com.flotalamacarena.telemetry.domain.ports

/**
 * Puerto para resolver la dirección IP local activa del dispositivo (Wi-Fi o Datos Móviles).
 */
interface IpResolver {
    fun resolveIpAddress(): String
}
