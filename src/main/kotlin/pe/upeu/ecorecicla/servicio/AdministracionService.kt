package pe.upeu.ecorecicla.servicio

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import pe.upeu.ecorecicla.config.Fallo
import pe.upeu.ecorecicla.repositorio.EcoRepository
import pe.upeu.ecorecicla.seguridad.*
import pe.upeu.ecorecicla.web.dto.*

@Service
class AdministracionService(private val repo: EcoRepository,private val users: UsuarioRepository,private val passwords: PasswordEncoder) {
    private val db get()=repo.db
    private fun paginaValida(p: Int,t: Int) { if(p<0 || t !in 1..100) throw Fallo(400,"Pagina >= 0 y tamanio entre 1 y 100") }
    private fun <T> pagina(p: Int,t: Int,total: Long,contenido: List<T>)=Pagina(contenido,p,t,total,((total+t-1)/t).toInt(),(p.toLong()+1)*t>=total)
    private fun respuesta(u: Usuario)=UsuarioRespuesta(u.id,u.username,u.nombre,u.rol,users.privilegios(u.rol),u.activo)
    private fun rolExiste(rol: String)=db.queryForObject("SELECT COUNT(*) FROM rol WHERE nombre=?",Long::class.java,rol)!!>0
    private fun bloquear() { db.queryForList("SELECT nombre FROM rol ORDER BY nombre FOR UPDATE",String::class.java) }
    private fun gestoresFueraDe(id: Long?=null,rol: String?=null): Long {
        val filtros=StringBuilder(); val params=mutableListOf<Any>()
        if(id!=null) { filtros.append(" AND u.id<>?"); params.add(id) }
        if(rol!=null) { filtros.append(" AND u.rol<>?"); params.add(rol) }
        return db.queryForObject("SELECT COUNT(DISTINCT u.id) FROM usuario u JOIN rol_privilegio rp ON rp.rol=u.rol WHERE u.activo AND rp.privilegio='PUNTO_GESTIONAR'$filtros",Long::class.java,*params.toTypedArray())!!
    }

    fun usuarios(p: Int,t: Int): Pagina<UsuarioRespuesta> {
        paginaValida(p,t)
        return pagina(p,t,db.queryForObject("SELECT COUNT(*) FROM usuario",Long::class.java)!!,users.listar(p,t).map(::respuesta))
    }
    fun roles(p: Int,t: Int): Pagina<RolRespuesta> {
        paginaValida(p,t)
        val roles=db.queryForList("SELECT nombre FROM rol ORDER BY nombre LIMIT ? OFFSET ?",String::class.java,t,p.toLong()*t)
        return pagina(p,t,db.queryForObject("SELECT COUNT(*) FROM rol",Long::class.java)!!,roles.map { RolRespuesta(it,users.privilegios(it)) })
    }
    fun privilegios(): Pagina<String> { val permisos=Privilegio.entries.map { it.name }; return Pagina(permisos,0,100,permisos.size.toLong(),1,true) }

    @Transactional
    fun guardarUsuario(id: Long?,b: UsuarioGestionPeticion): UsuarioRespuesta {
        bloquear()
        val anterior=id?.let { users.porId(it) ?: throw Fallo(404,"Usuario inexistente") }
        if(!rolExiste(b.rol)) throw Fallo(400,"Datos invalidos",mapOf("rol" to "El rol no existe"))
        if(users.buscar(b.usuario)?.let { it.id!=id }==true) throw Fallo(409,"El nombre de usuario ya existe")
        val nuevoGestor=b.activo && "PUNTO_GESTIONAR" in users.privilegios(b.rol)
        if(anterior!=null && anterior.activo && "PUNTO_GESTIONAR" in users.privilegios(anterior.rol) && !nuevoGestor && gestoresFueraDe(id=anterior.id)==0L)
            throw Fallo(409,"Debe conservar al menos un usuario activo con PUNTO_GESTIONAR")
        if(anterior==null && b.password==null) throw Fallo(400,"Datos invalidos",mapOf("password" to "La contrasena es obligatoria"))
        b.password?.let { if(it.length<8 || it.toByteArray(Charsets.UTF_8).size>72) throw Fallo(400,"Datos invalidos",mapOf("password" to "Minimo 8 caracteres y maximo 72 bytes UTF-8")) }
        val hash=b.password?.let(passwords::encode) ?: anterior!!.password
        val usuarioId=if(anterior==null) repo.insertar("INSERT INTO usuario(username,password,nombre,rol,activo) VALUES (?,?,?,?,?)",b.usuario,hash,b.nombre,b.rol,b.activo)
        else {
            db.update("UPDATE usuario SET username=?,password=?,nombre=?,rol=?,activo=?,version_sesion=version_sesion+1 WHERE id=?",b.usuario,hash,b.nombre,b.rol,b.activo,anterior.id)
            anterior.id
        }
        return respuesta(users.porId(usuarioId)!!)
    }
    @Transactional
    fun desactivarUsuario(id: Long) {
        val u=users.porId(id) ?: throw Fallo(404,"Usuario inexistente")
        guardarUsuario(id,UsuarioGestionPeticion(u.username,u.nombre,u.rol,activo=false))
    }
    @Transactional
    fun guardarRol(actual: String?,b: RolGestionPeticion): RolRespuesta {
        bloquear()
        if(actual!=null && !rolExiste(actual)) throw Fallo(404,"Rol inexistente")
        if(actual!=null && actual!=b.nombre) throw Fallo(400,"Datos invalidos",mapOf("nombre" to "El identificador del rol no se cambia"))
        if(actual==null && rolExiste(b.nombre)) throw Fallo(409,"El rol ya existe")
        val desconocidos=b.privilegios-Privilegio.entries.map { it.name }.toSet()
        if(desconocidos.isNotEmpty()) throw Fallo(400,"Datos invalidos",mapOf("privilegios" to "Privilegios desconocidos: ${desconocidos.joinToString()}"))
        if(actual!=null && "PUNTO_GESTIONAR" in users.privilegios(actual) && "PUNTO_GESTIONAR" !in b.privilegios && gestoresFueraDe(rol=actual)==0L)
            throw Fallo(409,"Debe conservar al menos un usuario activo con PUNTO_GESTIONAR")
        if(actual==null) db.update("INSERT INTO rol(nombre) VALUES (?)",b.nombre)
        db.update("DELETE FROM rol_privilegio WHERE rol=?",b.nombre)
        b.privilegios.forEach { db.update("INSERT INTO rol_privilegio(rol,privilegio) VALUES (?,?)",b.nombre,it) }
        db.update("UPDATE usuario SET version_sesion=version_sesion+1 WHERE rol=?",b.nombre)
        return RolRespuesta(b.nombre,b.privilegios)
    }
    @Transactional
    fun eliminarRol(nombre: String) {
        bloquear()
        if(!rolExiste(nombre)) throw Fallo(404,"Rol inexistente")
        if(db.queryForObject("SELECT COUNT(*) FROM usuario WHERE rol=?",Long::class.java,nombre)!!>0) throw Fallo(409,"El rol tiene usuarios asignados")
        db.update("DELETE FROM rol_privilegio WHERE rol=?",nombre)
        db.update("DELETE FROM rol WHERE nombre=?",nombre)
    }
}
