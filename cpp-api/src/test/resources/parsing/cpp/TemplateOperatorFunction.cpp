namespace std
{
  template<typename _Ch, typename _Tr, typename _Tp, _Lock_policy _Lp>
    inline std::basic_ostream<_Ch, _Tr>&
    operator<<(std::basic_ostream<_Ch, _Tr>& __os,
	       const __shared_ptr<_Tp, _Lp>& __p)
    {
      __os << __p.get();
      return __os;
    }

  template<typename _Tp>
    class shared_ptr
    {
    };
}

template<typename _Del, typename _Tp, _Lock_policy _Lp>
  inline _Del*
  get_deleter(const __shared_ptr<_Tp, _Lp>& __p) noexcept
  {
    return 0;
  }
