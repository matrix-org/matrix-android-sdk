package org.matrix.androidsdk.lazyloading;

import android.support.test.InstrumentationRegistry;

import junit.framework.Assert;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.common.CommonTestHelper;
import org.matrix.androidsdk.common.TestApiCallback;
import org.matrix.androidsdk.rest.model.search.SearchResponse;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;

@FixMethodOrder(MethodSorters.JVM)
public class SearchTest {

    private CommonTestHelper mTestHelper = new CommonTestHelper();
    private LazyLoadingTestHelper mLazyLoadingTestHelper = new LazyLoadingTestHelper(mTestHelper);

    @BeforeClass
    public static void init() {
        MXSession.initUserAgent(InstrumentationRegistry.getContext(), null);
    }

    @Test
    public void Search_CheckMessageSearch_LazyLoadedMembers() throws Exception {
        Search_CheckMessageSearch_(true);
    }

    @Test
    public void Search_CheckMessageSearch_LoadAllMembers() throws Exception {
        Search_CheckMessageSearch_(false);
    }

    private void Search_CheckMessageSearch_(boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
        final CountDownLatch lock = new CountDownLatch(1);
        data.aliceSession.pauseEventStream();
        data.aliceSession.resumeEventStream();
        data.aliceSession.getDataHandler().clear();
        data.aliceSession.searchMessageText("Bob message", Collections.singletonList(data.roomId), 0, 0, null, new TestApiCallback<SearchResponse>(lock) {
            @Override
            public void onSuccess(SearchResponse info) {
                Assert.assertEquals(1, info.searchCategories.roomEvents.results.size());
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock);
        mLazyLoadingTestHelper.clearAllSessions(data);
    }

}
