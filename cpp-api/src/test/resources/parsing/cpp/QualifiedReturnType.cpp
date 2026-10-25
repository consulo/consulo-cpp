std::locale path::imbue(std::locale const& loc)
{
    return loc;
}

__attribute__((visibility("default"))) std::locale path::imbue2(std::locale const& loc)
{
    return loc;
}

__attribute__((visibility("default"))) path::codecvt_type const& path::codecvt()
{
    return codecvt_facet;
}
