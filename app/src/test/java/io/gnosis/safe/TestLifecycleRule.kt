package io.gnosis.safe

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.runner.Description

class TestLifecycleRule : InstantTaskExecutorRule(), LifecycleOwner {

    private val lifecycle = LifecycleRegistry(this)

    override fun getLifecycle() = lifecycle

    override fun starting(description: Description?) {
        super.starting(description)
        Dispatchers.setMain(TestCoroutineDispatcher())
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun finished(description: Description?) {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        Dispatchers.resetMain()
        super.finished(description)
    }

}
