package dev.haja.buckpal.account.domain;

import dev.haja.buckpal.account.domain.Account.AccountId;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 계정 활동 윈도우.
 */
public class ActivityWindow {

    /**
     * 활동 목록.
     */
    private final List<Activity> activities;

    public ActivityWindow(List<Activity> activities) {
        this.activities = activities;
    }
    public ActivityWindow(@NonNull Activity... activities) {
        this.activities = new ArrayList<>(Arrays.asList(activities));
    }

    /**
     * 활동 윈도우의 활동을 기반으로 계정의 총 잔액을 계산한다.
     * @param accountId 계정 ID
     * @return 계정의 총 잔액
     */
    public Money calculateBalance(AccountId accountId) {


        return null;
    }

    public void addActivity(Activity activity) {
        this.activities.add(activity);
    }
}
