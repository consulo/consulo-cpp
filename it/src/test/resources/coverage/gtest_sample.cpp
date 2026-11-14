#include <gtest/gtest.h>

#include <map>
#include <stdexcept>
#include <string>
#include <vector>

namespace bank {

class Account {
public:
    explicit Account(std::string owner) : owner_(std::move(owner)) {}

    void deposit(long amount) {
        if (amount <= 0) {
            throw std::invalid_argument("amount");
        }
        balance_ += amount;
    }

    long balance() const { return balance_; }
    const std::string& owner() const { return owner_; }

private:
    std::string owner_;
    long balance_ = 0;
};

} // namespace bank

class AccountTest : public ::testing::Test {
protected:
    void SetUp() override { account_.deposit(10); }

    bank::Account account_{"ann"};
    std::map<std::string, int> limits_{{"daily", 100}};
};

TEST(AccountBasics, StartsEmpty) {
    bank::Account account("bob");
    EXPECT_EQ(account.balance(), 0);  //> balance -> Account
    EXPECT_EQ(account.owner(), "bob");
}

TEST_F(AccountTest, DepositAdds) {
    account_.deposit(5);  //> deposit -> Account
    ASSERT_EQ(account_.balance(), 15);
    EXPECT_GT(limits_.at("daily"), 0);
}

TEST_F(AccountTest, RejectsNegative) {
    EXPECT_THROW(account_.deposit(-1), std::invalid_argument);
    EXPECT_TRUE(account_.owner().size() == 3);
}

class ParityTest : public ::testing::TestWithParam<int> {};

TEST_P(ParityTest, EvenOrOdd) {
    int value = GetParam();
    std::vector<int> values{value, value + 1};
    EXPECT_NE(values.front() % 2, values.back() % 2);
}

INSTANTIATE_TEST_SUITE_P(Numbers, ParityTest, ::testing::Values(1, 2, 3));
