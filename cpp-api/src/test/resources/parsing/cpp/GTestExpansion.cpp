void CalcTest_AddsNumbers_Test::TestBody()
{
    switch (0) case 0: default: if (const ::testing::AssertionResult gtest_ar = (::testing::internal::EqHelper::Compare("calc::add(2, 3)", "5", calc::add(2, 3), 5))) ; else ::testing::internal::AssertHelper(::testing::TestPartResult::kNonFatalFailure, "calc_test.cpp", 7, gtest_ar.failure_message()) = ::testing::Message();
}

void CalcTest_DivisionByZeroThrows_Test::TestBody()
{
    switch (0) case 0: default: if (::testing::internal::TrueWithString gtest_msg{}) { bool gtest_caught_expected = false; try { if (::testing::internal::AlwaysTrue()) { calc::divide(1, 0); } else static_assert(true, ""); } catch (std::invalid_argument const&) { gtest_caught_expected = true; } catch (...) { gtest_msg.value = "Actual: it throws a different type."; goto gtest_label_testthrow_18; } if (!gtest_caught_expected) { goto gtest_label_testthrow_18; } } else gtest_label_testthrow_18 : ::testing::internal::AssertHelper(::testing::TestPartResult::kNonFatalFailure, "calc_test.cpp", 18, gtest_msg.value.c_str()) = ::testing::Message();
}
