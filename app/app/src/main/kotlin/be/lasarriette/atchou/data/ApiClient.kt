package be.lasarriette.atchou.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class Post(
  val id: Int,
  val author: String = "",
  val sender: String = "",
  @SerialName("created_at") val createdAt: String = "",
  @SerialName("show_on") val showOn: String? = null,
  val pinned: Boolean = false,
  @SerialName("exclude_from_rotation") val excludeFromRotation: Boolean = false,
  @SerialName("show_author") val showAuthor: Boolean = false,
  @SerialName("image_url") val imageUrl: String,
)

data class PostOptions(
  val author: String = "",
  val sender: String = "",
  val showAuthor: Boolean = false,
  val showOn: String? = null,
  val pinned: Boolean = false,
  val excludeFromRotation: Boolean = false,
  val mode: String = "dither",
)

@Serializable
data class AuthConfig(
  @SerialName("auth_required") val authRequired: Boolean = false,
  @SerialName("google_client_id") val googleClientId: String? = null,
)

@Serializable
data class LoginResult(val token: String, val email: String = "", val name: String = "")

class LoginRejectedException : IOException("compte non autorisé")

class ApiClient(
  private val baseUrl: () -> String,
  private val token: () -> String? = { null },
  private val onUnauthorized: () -> Unit = {},
) {
  private val json = Json { ignoreUnknownKeys = true }
  private val http = OkHttpClient.Builder()
    .callTimeout(60, TimeUnit.SECONDS)
    .addInterceptor { chain ->
      val request = chain.request().newBuilder().apply { authHeader()?.let { header("Authorization", it) } }.build()
      chain.proceed(request).also { if (it.code == 401 && request.url.encodedPath != "/auth/google") onUnauthorized() }
    }
    .build()

  fun url(path: String) = baseUrl() + path

  fun authHeader(): String? = token()?.let { "Bearer $it" }

  suspend fun authConfig(): AuthConfig = withContext(Dispatchers.IO) {
    execute(Request.Builder().url(url("/auth/config")).build()).use { json.decodeFromString(it.body!!.string()) }
  }

  suspend fun loginGoogle(idToken: String): LoginResult = withContext(Dispatchers.IO) {
    val body = """{"id_token":"$idToken"}""".toRequestBody("application/json".toMediaType())
    val response = http.newCall(Request.Builder().url(url("/auth/google")).post(body).build()).execute()
    response.use {
      if (it.code == 403) throw LoginRejectedException()
      if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
      json.decodeFromString<LoginResult>(it.body!!.string())
    }
  }

  private fun execute(request: Request): okhttp3.Response {
    val response = http.newCall(request).execute()
    if (!response.isSuccessful) {
      val code = response.code
      response.close()
      throw IOException("HTTP $code")
    }
    return response
  }

  private fun form(image: ByteArray, o: PostOptions, withPostFields: Boolean): MultipartBody {
    val b = MultipartBody.Builder().setType(MultipartBody.FORM)
      .addFormDataPart("image", "image.jpg", image.toRequestBody("image/jpeg".toMediaType()))
      .addFormDataPart("author", o.author)
      .addFormDataPart("show_author", o.showAuthor.toString())
      .addFormDataPart("mode", o.mode)
    if (withPostFields) {
      b.addFormDataPart("sender", o.sender)
        .addFormDataPart("pinned", o.pinned.toString())
        .addFormDataPart("exclude_from_rotation", o.excludeFromRotation.toString())
      o.showOn?.let { b.addFormDataPart("show_on", it) }
    }
    return b.build()
  }

  suspend fun listPosts(): List<Post> = withContext(Dispatchers.IO) {
    execute(Request.Builder().url(url("/posts")).build()).use {
      json.decodeFromString(it.body!!.string())
    }
  }

  suspend fun authors(): List<String> = withContext(Dispatchers.IO) {
    execute(Request.Builder().url(url("/authors")).build()).use {
      json.decodeFromString(it.body!!.string())
    }
  }

  suspend fun preview(image: ByteArray, o: PostOptions): ByteArray = withContext(Dispatchers.IO) {
    val req = Request.Builder().url(url("/preview")).post(form(image, o, false)).build()
    execute(req).use { it.body!!.bytes() }
  }

  suspend fun createPost(image: ByteArray, o: PostOptions): Post = withContext(Dispatchers.IO) {
    val req = Request.Builder().url(url("/posts")).post(form(image, o, true)).build()
    execute(req).use { json.decodeFromString(it.body!!.string()) }
  }

  suspend fun deletePost(id: Int) = withContext(Dispatchers.IO) {
    execute(Request.Builder().url(url("/posts/$id")).delete().build()).close()
  }
}
