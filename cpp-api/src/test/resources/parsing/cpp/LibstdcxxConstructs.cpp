namespace std
{
  template<_Lock_policy _Lp>
    class __shared_count
    {
      __shared_count(const __weak_count<_Lp>& __r);
    };

  template<_Lock_policy _Lp>
    inline
    __shared_count<_Lp>::__shared_count(const __weak_count<_Lp>& __r)
    : _M_pi(__r._M_pi)
    {
    }

  void f(void* __mem)
  {
    auto __pi = ::new (__mem)
      _Sp_cp_type(__a._M_a, std::forward<_Args>(__args)...);
    this->__shared_ptr<_Tp>::operator=(__r);
  }

  template<typename _Tp, typename... _Ti, size_t... _Ind>
    struct _Build_FUNs<_Tp, variant<_Ti...>, index_sequence<_Ind...>>
    : _Build_FUN<_Ind, _Tp, _Ti>...
    {
      using _Build_FUN<_Ind, _Tp, _Ti>::_S_fun...;
    };

  template<typename... _Types>
    constexpr bool
    operator== [[nodiscard]] (const variant<_Types...>& __lhs,
			      const variant<_Types...>& __rhs)
    {
      namespace __variant = __detail::__variant;
      return __variant::__compare(true, __lhs, __rhs);
    }

  class after
  {
  };
}
