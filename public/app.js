Vue.component('sticker', {
  props: ['item'],
  template: '#sticker-template',
  methods: {
    saveItem: function() {
      this.$emit('save', this.item)
    },
    thumbUrl: function(item) {
      let set = (item.type == 'sticker') ? item.stickerSet : ('!' + item.type);
      return '/thumbs/' + set + '/' + item.uniqueId + '.png';
    }
  }
})

const app = new Vue({
  el: '#app',
  data() {
    return {
      accessKey: "0",
      meta: {appName: "Bot", bot: "botfather", user: ""},
      stickerSet: "",
      items: []
    }
  },
  mounted() {
    let uri = window.location.search.substring(1);
    let params = new URLSearchParams(uri);
    this.accessKey = (params.get("d") || "0")
    fetch('/meta/' + this.accessKey)
      .then(resp => resp.json())
      .then(data => this.meta = data)
  },
  computed: {
    authorized: function() {
      return this.meta.user.length > 0
    }
  },
  methods: {
    onStickerSetChange: function() {
      this.items = []
      fetch('/items/' + this.stickerSet, {
        method: 'GET',
        headers: {
          'guid': this.accessKey
        },
      }).then(resp => resp.json())
        .then(data => this.items = data.map(it =>
                 Object.assign(it, {wait: false, status: ""})
                 ))
    },
    save: function(item) {
      item.status = ""
      item.wait = true
      fetch('/items', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json;charset=utf-8',
          'guid': this.accessKey
        },
        body: JSON.stringify({
          uniqueId: item.uniqueId,
          tags: item.tag || ""
        })
      }).then(resp => {
        item.wait = false
        switch (resp.status) {
          case 200: item.status = "Saved"; break;
          case 201: item.status = "Added"; break;
          case 205: item.status = "Removed"; break;
          case 401: meta.user = ""; break;
        }
      }).catch(err => {
        item.wait = false
      })
    }
  }
});