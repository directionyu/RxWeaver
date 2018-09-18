@file:Suppress("UNREACHABLE_CODE")

package com.github.qingmei2

import android.support.v4.app.FragmentActivity
import android.widget.Toast
import com.github.qingmei2.core.GlobalErrorTransformer
import com.github.qingmei2.core.RxThrowable
import com.github.qingmei2.model.NavigatorFragment
import com.github.qingmei2.model.RxDialog
import com.github.qingmei2.retry.RetryConfig
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import org.json.JSONException
import java.net.ConnectException

object WeaverHelper {

    /**
     * Status code
     */
    private const val STATUS_OK = 200
    private const val STATUS_UNAUTHORIZED = 401
    private const val FORBIDDEN = 403
    private const val NOT_FOUND = 404
    private const val REQUEST_TIMEOUT = 408
    private const val INTERNAL_SERVER_ERROR = 500
    private const val BAD_GATEWAY = 502
    private const val SERVICE_UNAVAILABLE = 503
    private const val GATEWAY_TIMEOUT = 504

    fun <T : BaseEntity> handleGlobalError(context: FragmentActivity): GlobalErrorTransformer<T> = GlobalErrorTransformer(

            upStreamSchedulerProvider = { AndroidSchedulers.mainThread() },

            downStreamSchedulerProvider = { AndroidSchedulers.mainThread() },

            // 通过onNext流中数据的状态进行操作
            globalOnNextRetryInterceptor = {
                when (it.statusCode) {
                    STATUS_UNAUTHORIZED -> {
                        Single.just(TokenExpiredException(it))
                    }
                    else -> Single.just(RxThrowable.EMPTY)
                }
            },

            // 通过onError中Throwable状态进行操作
            globalOnErrorResume = { error ->
                when (error) {
                    is ConnectException -> {
                        Observable.error<T>(ConnectFailedAlertDialogException())
                    }
                    else -> Observable.error<T>(error)
                }
            },

            retryErrorTransformer = { error ->
                when (error) {
                    is TokenExpiredException -> {
                        Toast.makeText(context, "Token失效，跳转到Login重新登录！", Toast.LENGTH_SHORT).show()
                        NavigatorFragment()
                                .startLoginForResult(context)
                                .flatMap { loginSuccess ->
                                    if (loginSuccess) {
                                        Toast.makeText(context, "登陆成功！3秒延迟后重试！", Toast.LENGTH_SHORT).show()
                                        Single.just(true)
                                    } else {
                                        Toast.makeText(context, "登陆失败！", Toast.LENGTH_SHORT).show()
                                        Single.just(false)
                                    }
                                }
                    }
                    is ConnectFailedAlertDialogException -> {
                        RxDialog.showErrorDialog(context, "ConnectException")
                                .flatMap {
                                    if (it)   // 用户选择重试按钮,发送重试事件
                                        Single.just(true)
                                    else
                                        Single.just(false)

                                }
                    }
                    else -> Single.just(false)
                }
            },

            retryConfigProvider = { error ->
                when (error) {
                    is ConnectFailedAlertDialogException -> RetryConfig(needTransform = true)
                    is TokenExpiredException -> RetryConfig(delay = 3000, needTransform = true)
                    else -> RetryConfig(needTransform = false) // 其它异常都不重试
                }
            },

            globalDoOnErrorConsumer = { error ->
                when (error) {
                    is JSONException -> {
                        Toast.makeText(context, "全局异常捕获-Json解析异常！", Toast.LENGTH_SHORT).show()
                    }
                    else -> {

                    }
                }
            }
    )
}