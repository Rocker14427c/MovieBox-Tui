package app.moviebox.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.moviebox.data.CatalogItem
import app.moviebox.ui.components.ErrorState
import app.moviebox.ui.components.FullScreenLoading
import app.moviebox.ui.components.PosterCard
import app.moviebox.ui.viewmodel.MovieBoxViewModel

@Composable
fun SearchScreen(vm: MovieBoxViewModel, onOpen: (CatalogItem) -> Unit) {
    val state by vm.search.collectAsState()
    val err = state.error
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { q -> query = q; vm.setSearchQuery(q) },
            placeholder = { androidx.compose.material3.Text("Search movies & TV shows…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            state.loading -> FullScreenLoading()
            err != null -> ErrorState(err) { vm.setSearchQuery(query) }
            state.results.isEmpty() && query.isNotBlank() ->
                androidx.compose.material3.Text(
                    "No results found.", modifier = Modifier.padding(24.dp),
                    color = androidx.compose.ui.graphics.Color.Gray,
                )
            state.results.isNotEmpty() -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            ) {
                items(state.results) { item ->
                    PosterCard(item, onClick = { onOpen(item) })
                }
            }
        }
    }
}
