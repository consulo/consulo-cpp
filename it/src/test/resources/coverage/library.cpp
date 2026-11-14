#include <chrono>
#include <deque>
#include <list>
#include <mutex>
#include <optional>
#include <string>
#include <string_view>
#include <thread>
#include <typeinfo>
#include <unordered_set>
#include <variant>
#include <vector>

namespace app::model {

struct Account {
    std::string owner;
    long balance = 0;

    static int created;

    bool operator==(const Account& other) const { return owner == other.owner; }
};

int Account::created = 0;

template <typename Derived>
class Printable {
public:
    std::string print() const { return std::string("printable ") + typeid(Derived).name(); }
};

class Ledger : public Printable<Ledger> {
public:
    void add(Account account) {
        std::lock_guard<std::mutex> lock(mutex_);
        accounts_.push_back(std::move(account));
        ++Account::created;
    }

    std::optional<Account> find(std::string_view owner) const {
        for (const Account& account : accounts_) {
            if (account.owner == owner) {
                return account;
            }
        }
        return std::nullopt;
    }

    std::string describe() const { return std::to_string(accounts_.size()) + " accounts"; }

    long total() const {
        long sum = 0;
        for (auto it = accounts_.begin(); it != accounts_.end(); ++it) {
            sum += it->balance;
        }
        return sum;
    }

private:
    mutable std::mutex mutex_;
    std::vector<Account> accounts_;
};

} // namespace app::model

namespace model = app::model;
using model::Account;

using Value = std::variant<int, double, std::string>;

struct Visitor {
    std::string operator()(int value) const { return std::to_string(value); }
    std::string operator()(double value) const { return std::to_string(value); }
    std::string operator()(const std::string& value) const { return value; }
};

int main() {
    model::Ledger ledger;
    ledger.add(Account{"ann", 10});
    ledger.add(Account{"bob", 20});

    if (auto found = ledger.find("ann")) {
        found->balance += 5;
        (void) found.value().owner.size();
    }
    (void) ledger.print().length();
    (void) ledger.total();

    std::deque<int> queue{1, 2, 3};
    queue.push_front(0);
    (void) queue.front();

    std::list<Account> list;
    list.emplace_back(Account{"cat", 1});
    (void) list.back().owner.empty();

    std::unordered_set<std::string> names{"ann"};
    names.insert("bob");
    (void) names.contains("ann");

    Value value = 2.5;
    std::string text = std::visit(Visitor{}, value);
    (void) std::holds_alternative<double>(value);
    (void) text.c_str();

    std::string_view view = text;
    (void) view.substr(0, 1).size();

    auto start = std::chrono::steady_clock::now();
    std::thread worker([&ledger] { ledger.add(Account{"dan", 3}); });
    worker.join();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start);
    (void) elapsed.count();

    return Account::created > 0 ? 0 : 1;
}
