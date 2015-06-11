#include <algorithm>
#include <iostream>
#include <limits>

#include "shard.hpp"
#include "term_iterator.hpp"

using namespace std;
using namespace imhotep;

int main(int argc, char *argv[])
{
    if (argc != 3) {
        cerr << "usage: " << argv[0] << " <shard dir> <field name>" << endl;
    }

    std::vector<std::string> int_terms;
    std::vector<std::string> str_terms;

    const string field_name(argv[2]);
    str_terms.push_back(field_name);
    const Shard shard(argv[1], int_terms, str_terms);

    StringTermIterator it(shard, field_name);
    StringTermIterator end;
    while (it != end) {
        cout << *it << endl;
        ++it;
    }
}
