package pe.upeu.ecorecicla.servicio

import pe.upeu.ecorecicla.config.Fallo
import java.math.BigDecimal

/** Regla unica para peticiones y semillas; el repositorio solo persiste el resultado. */
object ReglasPesaje {
    fun validarHumedad(valor: Double?) {
        if(valor!=null && (!valor.isFinite() || valor<0 || valor>100 || BigDecimal.valueOf(valor).stripTrailingZeros().scale()>2))
            throw Fallo(400,"Datos invalidos",mapOf("humedadEstimada" to "Usa un porcentaje de 0 a 100 con hasta 2 decimales"))
    }
    fun calcularNeto(bruto: Double, tara: Double): Double {
        val campos=mutableMapOf<String,String>()
        fun decimal(valor: Double,campo: String): BigDecimal? {
            if(!valor.isFinite()) { campos[campo]="Debe ser un numero finito"; return null }
            val d=BigDecimal.valueOf(valor).stripTrailingZeros()
            if(d.scale()>3 || d.precision()-d.scale()>11) campos[campo]="Maximo 11 enteros y 3 decimales"
            return d
        }
        val b=decimal(bruto,"pesoBruto"); val t=decimal(tara,"tara")
        if(b!=null && b.signum()<=0) campos["pesoBruto"]="Debe ser mayor que cero"
        if(t!=null && (t.signum()<0 || (b!=null && t>=b))) campos["tara"]="La tara debe ser no negativa y menor que el peso bruto"
        if(campos.isNotEmpty()) throw Fallo(400,"Datos invalidos",campos)
        return (b!!-t!!).toDouble()
    }
}
