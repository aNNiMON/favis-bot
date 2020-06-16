const app = new Vue({
  el: '#app',
  data: {
    items: []
  },
  created: function() {
    this.allItems()
  },
  methods: {
    allItems: function(event) {
      fetch('/items/')
        .then(resp => resp.json())
        .then(data => this.items = data);
    },
    thumbUrl: function(item) {
      return '/thumbs/' + item.stickerSet + '/' + item.id + '.png';
    }
  }
});