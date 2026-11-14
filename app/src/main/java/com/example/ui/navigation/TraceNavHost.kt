package com.example.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.permission.CameraPermissionScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.TraceMotion
import com.example.ui.theme.rememberAnimationsEnabled
import com.example.ui.trace.TraceScreen
import com.example.ui.trace.TraceViewModel

object TraceRoutes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ARG_IMAGE_ID = "imageId"
    const val ARG_PROJECT_ID = "projectId"

    const val PERMISSION = "permission?imageId={$ARG_IMAGE_ID}&projectId={$ARG_PROJECT_ID}"
    const val TRACE = "trace?imageId={$ARG_IMAGE_ID}&projectId={$ARG_PROJECT_ID}"

    fun permissionRoute(imageId: String = "", projectId: String = ""): String =
        "permission?imageId=$imageId&imageId_unused=0&projectId=$projectId".let {
            "permission?imageId=$imageId&projectId=$projectId"
        }

    fun traceRoute(imageId: String = "", projectId: String = ""): String =
        "trace?imageId=$imageId&projectId=$projectId"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TraceNavHost(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val animationsEnabled = rememberAnimationsEnabled()

    val navigateToTraceOrPermission = { imageId: String, projectId: String ->
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCameraPermission) {
            navController.navigate(TraceRoutes.traceRoute(imageId = imageId, projectId = projectId))
        } else {
            navController.navigate(TraceRoutes.permissionRoute(imageId = imageId, projectId = projectId))
        }
    }

    val containerDuration = TraceMotion.duration(TraceMotion.DURATION_CONTAINER_TRANSFORM, animationsEnabled)
    val sharedAxisDuration = TraceMotion.duration(TraceMotion.DURATION_SHARED_AXIS, animationsEnabled)
    val sharedAxisOutFade = TraceMotion.duration(90, animationsEnabled)
    val sharedAxisInFade = TraceMotion.duration(210, animationsEnabled)

    SharedTransitionLayout(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = TraceRoutes.HOME,
            enterTransition = {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = containerDuration,
                        easing = TraceMotion.EmphasizedEasing
                    )
                )
            },
            exitTransition = {
                fadeOut(
                    animationSpec = tween(
                        durationMillis = containerDuration,
                        easing = TraceMotion.EmphasizedEasing
                    )
                )
            },
            popEnterTransition = {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = containerDuration,
                        easing = TraceMotion.EmphasizedEasing
                    )
                )
            },
            popExitTransition = {
                scaleOut(
                    targetScale = 0.90f,
                    animationSpec = tween(
                        durationMillis = containerDuration,
                        easing = TraceMotion.EmphasizedEasing
                    )
                ) + slideOutHorizontally(
                    targetOffsetX = { fullWidth -> (fullWidth * 0.10f).toInt() },
                    animationSpec = tween(
                        durationMillis = containerDuration,
                        easing = TraceMotion.EmphasizedEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = containerDuration,
                        easing = TraceMotion.EmphasizedEasing
                    )
                )
            }
        ) {
            composable(
                route = TraceRoutes.HOME,
                exitTransition = {
                    when (targetState.destination.route) {
                        TraceRoutes.SETTINGS -> {
                            // Shared axis X: outgoing fades to 0 in first 90 ms while sliding over 300 ms
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -(fullWidth * 0.15f).toInt() },
                                animationSpec = tween(
                                    durationMillis = sharedAxisDuration,
                                    easing = TraceMotion.EmphasizedEasing
                                )
                            ) + fadeOut(
                                animationSpec = tween(
                                    durationMillis = sharedAxisOutFade,
                                    easing = TraceMotion.StandardAccelerateEasing
                                )
                            )
                        }
                        else -> {
                            fadeOut(
                                animationSpec = tween(
                                    durationMillis = containerDuration,
                                    easing = TraceMotion.EmphasizedEasing
                                )
                            )
                        }
                    }
                },
                popEnterTransition = {
                    when (initialState.destination.route) {
                        TraceRoutes.SETTINGS -> {
                            // Shared axis X return: incoming fades in after 90 ms while sliding over 300 ms
                            slideInHorizontally(
                                initialOffsetX = { fullWidth -> -(fullWidth * 0.15f).toInt() },
                                animationSpec = tween(
                                    durationMillis = sharedAxisDuration,
                                    easing = TraceMotion.EmphasizedEasing
                                )
                            ) + fadeIn(
                                animationSpec = tween(
                                    durationMillis = sharedAxisInFade,
                                    delayMillis = sharedAxisOutFade,
                                    easing = TraceMotion.StandardDecelerateEasing
                                )
                            )
                        }
                        else -> {
                            fadeIn(
                                animationSpec = tween(
                                    durationMillis = containerDuration,
                                    easing = TraceMotion.EmphasizedEasing
                                )
                            )
                        }
                    }
                }
            ) {
                val homeViewModel: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = homeViewModel,
                    onOpenRecentTrace = { imageId ->
                        navigateToTraceOrPermission(imageId, "")
                    },
                    onOpenProjectTrace = { projectId ->
                        navigateToTraceOrPermission("", projectId)
                    },
                    onOpenSettings = {
                        navController.navigate(TraceRoutes.SETTINGS)
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable
                )
            }

            composable(
                route = TraceRoutes.SETTINGS,
                enterTransition = {
                    // Shared axis X forward: 300 ms slide, incoming fades in over 210 ms after 90 ms
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> (fullWidth * 0.15f).toInt() },
                        animationSpec = tween(
                            durationMillis = sharedAxisDuration,
                            easing = TraceMotion.EmphasizedEasing
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = sharedAxisInFade,
                            delayMillis = sharedAxisOutFade,
                            easing = TraceMotion.StandardDecelerateEasing
                        )
                    )
                },
                popExitTransition = {
                    // Shared axis X backward + predictive back shrink & slide: outgoing fades to 0 in first 90 ms while sliding & shrinking over 300 ms
                    scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(
                            durationMillis = sharedAxisDuration,
                            easing = TraceMotion.EmphasizedEasing
                        )
                    ) + slideOutHorizontally(
                        targetOffsetX = { fullWidth -> (fullWidth * 0.15f).toInt() },
                        animationSpec = tween(
                            durationMillis = sharedAxisDuration,
                            easing = TraceMotion.EmphasizedEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = sharedAxisOutFade,
                            easing = TraceMotion.StandardAccelerateEasing
                        )
                    )
                }
            ) {
                val settingsViewModel: SettingsViewModel = viewModel()
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = TraceRoutes.PERMISSION,
                arguments = listOf(
                    navArgument(TraceRoutes.ARG_IMAGE_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(TraceRoutes.ARG_PROJECT_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = {
                    // Fade through 300 ms
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = sharedAxisInFade,
                            delayMillis = sharedAxisOutFade,
                            easing = TraceMotion.StandardDecelerateEasing
                        )
                    ) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = tween(
                            durationMillis = sharedAxisInFade,
                            delayMillis = sharedAxisOutFade,
                            easing = TraceMotion.EmphasizedDecelerateEasing
                        )
                    )
                },
                exitTransition = {
                    // Fade through 300 ms: outgoing fades out in first 90 ms
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = sharedAxisOutFade,
                            easing = TraceMotion.StandardAccelerateEasing
                        )
                    )
                },
                popExitTransition = {
                    scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(
                            durationMillis = sharedAxisDuration,
                            easing = TraceMotion.EmphasizedEasing
                        )
                    ) + slideOutHorizontally(
                        targetOffsetX = { fullWidth -> (fullWidth * 0.12f).toInt() },
                        animationSpec = tween(
                            durationMillis = sharedAxisDuration,
                            easing = TraceMotion.EmphasizedEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = sharedAxisOutFade,
                            easing = TraceMotion.StandardAccelerateEasing
                        )
                    )
                }
            ) { backStackEntry ->
                val imageId = backStackEntry.arguments?.getString(TraceRoutes.ARG_IMAGE_ID).orEmpty()
                val projectId = backStackEntry.arguments?.getString(TraceRoutes.ARG_PROJECT_ID).orEmpty()
                CameraPermissionScreen(
                    onPermissionGranted = {
                        if (imageId.isNotBlank() || projectId.isNotBlank()) {
                            navController.navigate(
                                TraceRoutes.traceRoute(imageId = imageId, projectId = projectId)
                            ) {
                                popUpTo(TraceRoutes.HOME) { inclusive = false }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = TraceRoutes.TRACE,
                arguments = listOf(
                    navArgument(TraceRoutes.ARG_IMAGE_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(TraceRoutes.ARG_PROJECT_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = {
                    if (initialState.destination.route == TraceRoutes.PERMISSION) {
                        // Permission screen to Trace screen: fade through, 300 ms
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = sharedAxisInFade,
                                delayMillis = sharedAxisOutFade,
                                easing = TraceMotion.StandardDecelerateEasing
                            )
                        ) + scaleIn(
                            initialScale = 0.92f,
                            animationSpec = tween(
                                durationMillis = sharedAxisInFade,
                                delayMillis = sharedAxisOutFade,
                                easing = TraceMotion.EmphasizedDecelerateEasing
                            )
                        )
                    } else {
                        // Home to Trace screen: 350 ms emphasized container transform
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = containerDuration,
                                easing = TraceMotion.EmphasizedEasing
                            )
                        )
                    }
                },
                popExitTransition = {
                    scaleOut(
                        targetScale = 0.90f,
                        animationSpec = tween(
                            durationMillis = containerDuration,
                            easing = TraceMotion.EmphasizedEasing
                        )
                    ) + slideOutHorizontally(
                        targetOffsetX = { fullWidth -> (fullWidth * 0.10f).toInt() },
                        animationSpec = tween(
                            durationMillis = containerDuration,
                            easing = TraceMotion.EmphasizedEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = containerDuration,
                            easing = TraceMotion.EmphasizedEasing
                        )
                    )
                }
            ) { backStackEntry ->
                val imageId = backStackEntry.arguments?.getString(TraceRoutes.ARG_IMAGE_ID).orEmpty()
                val projectId = backStackEntry.arguments?.getString(TraceRoutes.ARG_PROJECT_ID).orEmpty()
                val traceViewModel: TraceViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    factory = TraceViewModel.Factory
                )
                TraceScreen(
                    imageId = imageId,
                    projectId = projectId,
                    viewModel = traceViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable
                )
            }
        }
    }
}
