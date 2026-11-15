package com.chipoodle.devilrpg.entity.goal;

public final class VillagerReturnPolicyTest {
    public static void main(String[] args) {
        comprobar("inside profession range does not start",
                !VillagerReturnPolicy.shouldStart(70.0D * 70.0D));
        comprobar("beyond range starts",
                VillagerReturnPolicy.shouldStart(90.0D * 90.0D));
        comprobar("return continues until 64",
                VillagerReturnPolicy.shouldContinue(65.0D * 65.0D));
        comprobar("return stops at 64",
                !VillagerReturnPolicy.shouldContinue(64.0D * 64.0D));
        System.out.println("VILLAGER_RETURN_POLICY_TEST: OK (4 assertions)");
    }

    private static void comprobar(String name, boolean ok) {
        if (!ok) throw new AssertionError(name);
    }
}
