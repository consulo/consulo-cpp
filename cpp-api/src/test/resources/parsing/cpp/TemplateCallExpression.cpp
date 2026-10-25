std::codecvt_type const& codecvt()
{
    return std::use_facet< std::codecvt< wchar_t, char, std::mbstate_t > >(get_path_locale());
}

void calls(std::tuple<int, long> t, int a, int b, int c, int d)
{
    auto first = std::get<0>(t);
    auto text = boost::lexical_cast<std::string>(a);
    int largest = std::numeric_limits<int>::max();
    std::vector<int> values = std::vector<int>{1, 2, 3};
    auto pointer = std::make_shared<std::pair<int, int>>(a, b);
    auto member = t.template get<1>();
    auto shared = std::shared_ptr<T>(p->enable_shared_from_this<T>::shared_from_this(), p);
    bool compare = a < b && c > (d);
    bool chained = a < b || c > d;
}
