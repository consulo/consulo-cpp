namespace fs {

EXPORT_DECL path::codecvt_type const& path::codecvt()
{
    return std::use_facet< std::codecvt< wchar_t, char, std::mbstate_t > >(get_path_locale());
}

EXPORT_DECL std::locale path::imbue(std::locale const& loc)
{
    std::locale* p = replace_path_locale(loc);
    return default_locale();
}

__attribute__((__visibility__("default"))) std::locale path::imbue_attributed(std::locale const& loc)
{
    return loc;
}

std::locale path::imbue_plain(std::locale const& loc)
{
    return loc;
}

}
