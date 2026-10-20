package com.ikeu.common.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CreditConstant 常量值验证。
 */
class CreditConstantTest {

    @Test
    void creditInitialShouldBe100() {
        assertEquals(100, CreditConstant.CREDIT_INITIAL);
    }

    @Test
    void creditFreezeThresholdShouldBe60() {
        assertEquals(60, CreditConstant.CREDIT_FREEZE_THRESHOLD);
    }

    @Test
    void creditFreezeDaysShouldBe3() {
        assertEquals(3, CreditConstant.CREDIT_FREEZE_DAYS);
    }

    @Test
    void rewardOnTimeShouldBe1() {
        assertEquals(1, CreditConstant.REWARD_ON_TIME);
    }

    @Test
    void rewardEarlyShouldBe5() {
        assertEquals(5, CreditConstant.REWARD_EARLY);
    }

    @Test
    void reasonTypeValuesShouldHaveExpectedStrings() {
        assertEquals("TIMEOUT", CreditConstant.ReasonType.TIMEOUT);
        assertEquals("COMPLAINT", CreditConstant.ReasonType.COMPLAINT);
        assertEquals("MANUAL", CreditConstant.ReasonType.MANUAL);
        assertEquals("REWARD", CreditConstant.ReasonType.REWARD);
        assertEquals("RECOVER", CreditConstant.ReasonType.RECOVER);
    }
}
