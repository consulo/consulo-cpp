BOOST_FILESYSTEM_DECL path path_algorithms::lexically_normal_v3(path const& p)
{
    return p;
}

BOOST_FILESYSTEM_DECL path::iterator path::begin() const
{
    return iterator();
}

BOOST_FILESYSTEM_DECL std::locale path::imbue(std::locale const& loc)
{
    return loc;
}

BOOST_FILESYSTEM_DECL void flush(path& p);
